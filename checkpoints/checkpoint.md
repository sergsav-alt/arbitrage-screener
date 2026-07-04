# Чекпоинт: Arbitrage Screener

**Дата:** 2026-06-28
**Статус:** Рабочий real-time viewer рыночных данных (Order Book / Rates / Cross-Rates) по трём биржам через WebSocket → SSE.

> ⚠️ Проект сильно изменился относительно предыдущего чекпоинта (2026-06-14).
> Раньше планировался автоматический детектор арбитража (`core/`: `ArbitrageDetector`,
> `SpreadCalculator`, `FeeCalculator`, `ArbitrageService.scan()` и т.д.) с таблицей
> возможностей на фронте. **Этого кода в текущей кодовой базе нет.** Сейчас backend —
> это шлюз котировок: подключается к биржам по WS и транслирует стакан, сделки и
> bookTicker на фронт через SSE. Расчёт спредов выполняется на клиенте (вкладка Cross-Rates).

---

## 1. Что это за проект

Инструмент для наблюдения за рынком в реальном времени по нескольким криптобиржам.

- **Backend (Spring Boot)** держит WebSocket-подключения к биржам (фьючерсы), нормализует
  данные в `Deal` / `OrderBook` / `BookTicker` и отдаёт их на фронт через Server-Sent Events.
  Дополнительно есть REST для snapshot стакана и списков бирж/символов.
- **Frontend (React + Ant Design)** показывает три вкладки:
  - **Order Book** — живой стакан + лента сделок (Time & Sales) + UTC-часы.
  - **Rates** — линейный график bid/ask одной биржи + REST-snapshot стакана.
  - **Cross-Rates** — 4 линии спредов между двумя биржами (внутренние A/B и кросс AB/BA).

### Стек

| Слой | Технологии |
|------|-----------|
| Backend | Java 21, Spring Boot 4.1.0 (`spring-boot-starter-webmvc`), Lombok, Jackson, Actuator |
| WS-клиент | `org.java-websocket:Java-WebSocket:1.6.0` (обёртка `ExchangeWebSocketClient` с GZIP + ping/pong) |
| БД | PostgreSQL + jOOQ-starter + Liquibase (миграции есть, но **код доступа к БД отсутствует**) |
| Frontend | React 19, TypeScript 4.9, Ant Design 6, Axios, Chart.js 4 + react-chartjs-2 |
| Сборка | Maven (backend), react-scripts/CRA (frontend) |

### Биржи (фьючерсные эндпоинты)

| Биржа | Enum | WS (ExchangeEnum) | Реальные эндпоинты в адаптере |
|-------|------|-------------------|-------------------------------|
| Binance | `BINANCE` | `wss://fstream.binance.com` | `…/public/ws/{sym}@depth20@500ms`, `…/market/ws/{sym}@aggTrade`, `…@bookTicker` |
| BingX | `BINGX` | `wss://open-api-swap.bingx.com/swap-market` | внутри `BingXAdapter` |
| MEXC | `MEXC` | `wss://contract.mexc.com/edge` | внутри `MexcAdapter` |

---

## 2. Архитектура backend

### Структура пакетов (`com.arbitrage.screener`)

```
BackendApplication.java                 # @SpringBootApplication
cache/
├── DealCache.java                      # In-memory кэш сделок, ключ exchange:symbol, TTL=30s
└── OrderBookCache.java                 # In-memory кэш стаканов, TTL=30s
config/
├── CoreConfig.java                     # Бины: RestClient, ExchangeManager
└── CorsConfig.java                     # CORS для фронта
controller/
├── StreamController.java               # GET /api/stream/{exchange}  (единый SSE: deal/orderbook/bookticker)
├── OrderBookController.java            # GET /api/{exchange}/ob       (единый REST snapshot через стратегии)
├── ExchangeController.java             # GET /api/exchange/connect|disconnect
├── MarketDataController.java           # GET /api/exchanges, GET /api/symbols
└── GlobalExceptionHandler.java         # @RestControllerAdvice (+ ExchangeApiException → 502)
exchange/
├── ExchangeAdapter.java                # Интерфейс адаптера биржи (+ shutdown())
├── ExchangeAdapterFactory.java         # Фабрика + кэш адаптеров (по ExchangeEnum)
├── ExchangeEnum.java                   # BINGX, MEXC, BINANCE (+ displayName, wsUrl, fromString())
├── ExchangeManager.java                # Жизненный цикл подключений, маршрутизация в кэши
├── ExchangeWebSocketClient.java        # Обёртка Java-WebSocket: GZIP-декод, ping→pong, колбэки
├── PriceNormalizer.java
├── depth/                              # Стратегии REST-snapshot стакана по биржам
│   ├── DepthSnapshotProvider.java      # Интерфейс стратегии
│   ├── ExchangeApiException.java       # Ошибка внешнего API биржи (→ HTTP 502)
│   ├── BinanceDepthSnapshotProvider.java
│   ├── BingXDepthSnapshotProvider.java
│   └── MexcDepthSnapshotProvider.java
├── binance/BinanceAdapter.java         # 3 независимых WS: depth20 / aggTrade / bookTicker
├── bingx/BingXAdapter.java
└── mexc/MexcAdapter.java
model/                                  # records (иммутабельные DTO)
├── Deal.java                           # exchange, symbol, price, quantity, timestamp (BigDecimal)
├── OrderBook.java                      # exchange, symbol, bids[], asks[], timestamp
├── OrderBookLevel.java                 # price, quantity
├── BookTicker.java                     # bid/ask price+qty, timestamp
├── BinanceDepthResponse.java           # DTO под REST-snapshot бирж
├── BingXDepthResponse.java
└── MexcDepthResponse.java
util/
├── FormatHelper.java                   # normalizeSymbol, formatSymbol, parseEventTime, build OB
└── ObjectMapperHelper.java             # общий ObjectMapper
```

### Ключевые архитектурные решения

1. **Adapter Pattern** — каждая биржа за интерфейсом `ExchangeAdapter` (connect/disconnect/subscribe/onDeal/onOrderBook/onBookTicker).
2. **Factory + кэш** — `ExchangeAdapterFactory` создаёт по одному адаптеру на биржу (`computeIfAbsent`).
3. **Транспорт отделён от логики** — `ExchangeWebSocketClient` инкапсулирует WS, GZIP и ping/pong, отдаёт события через колбэки.
4. **Observer через `Consumer<>`** — адаптеры уведомляют подписчиков о `Deal`/`OrderBook`/`BookTicker`.
5. **Records + BigDecimal** — иммутабельные DTO, деньги без `double`.
6. **SSE для real-time** — `AbstractSseController` рассылает события всем подключённым `SseEmitter`.
7. **Автопереподключение** — экспоненциальный backoff 1→2→4…→60 c, отдельно по каждому WS-каналу (в Binance — 3 канала). Реконнект идемпотентен: guard `AtomicBoolean` исключает параллельные попытки от `onError`+`onClose`, флаг `closing` подавляет реконнект при намеренном отключении, отложенная задача отменяется через `ScheduledFuture`.
9. **Graceful shutdown** — `ExchangeManager.@PreDestroy` закрывает активные подключения и через `ExchangeAdapterFactory.shutdownAll()` → `ExchangeAdapter.shutdown()` гасит планировщики адаптеров (без утечки потоков/сессий при остановке).
8. **Несколько одновременных подключений** — `ExchangeManager` держит по одному активному соединению на биржу (`Map<ExchangeEnum, ActiveConnection>`); повторный connect к той же бирже заменяет старое соединение, другие не трогает.

### Поток данных

```
Биржа (WS) → {Exchange}Adapter (парсинг JSON, нормализация)
  → Deal / OrderBook / BookTicker
  → AbstractSseController.broadcast(event, data)   (на каждый адаптер регистрируются обработчики)
  → SSE (text/event-stream) → фронт (EventSource)
  ⇣ (параллельно)
  → ExchangeManager: DealCache.put / OrderBookCache.put (in-memory, TTL=30s)
```

> Примечание: `AbstractSseController` и `ExchangeManager` независимо вешают свои обработчики
> на адаптер. Кэши (`DealCache`/`OrderBookCache`) наполняются, но сейчас **из них никто не читает**
> в рамках бизнес-логики (нет детектора/статистики, которые бы их потребляли).

---

## 3. REST / SSE API

| Метод | Эндпоинт | Назначение |
|-------|----------|-----------|
| GET | `/api/exchanges` | Список бирж со статусом подключения (`exchange`, `displayName`, `connected`) |
| GET | `/api/symbols` | Список символов из конфига, в формате с дефисом (`BTC-USDC`) |
| GET | `/api/exchange/connect?exchange=&symbol=` | Подключиться к бирже и подписаться на символ |
| GET | `/api/exchange/disconnect?exchange=` | Отключить биржу |
| GET | `/api/stream/{exchange}` | SSE-поток: события `deal`, `orderbook`, `bookticker` |
| GET | `/api/{exchange}/ob?symbol=&depth=` | REST-snapshot стакана (по умолчанию depth=20) |
| GET | `/actuator/health`, `/actuator/metrics` | Actuator |

---

## 4. Архитектура frontend

```
frontend/src/
├── App.tsx                     # Tabs: Order Book / Rates / Cross-Rates
├── chartSetup.ts               # Регистрация Chart.js
├── constants.ts                # EXCHANGE_OPTIONS (BINANCE, BINGX, MEXC)
├── components/
│   ├── AppLayout.tsx           # Header + Sider(ExchangeStatus) + Content
│   ├── ExchangeStatus.tsx      # Бейджи статуса бирж (poll каждые 10 c)
│   ├── OrderBookPanel.tsx      # Живой стакан + T&S лента + UTC-часы (SSE: deal+orderbook)
│   ├── OrderBookTable.tsx      # Таблица snapshot-стакана (REST), используется в RateChart
│   ├── RateChart.tsx           # График bid/ask (SSE: bookticker) + snapshot стакана
│   └── CrossRatesChart.tsx     # 4 линии спредов между двумя биржами (SSE: bookticker ×2)
├── services/
│   └── api.ts                  # REST + 2 варианта SSE-подписки
└── types/
    └── index.ts                # ExchangeStatus, TickerData, DealData, OrderBookData, ...
```

### Особенности фронта

- **`subscribeStream`** (Order Book) слушает `deal` + `orderbook`; **`subscribeBookTickerStream`** (Rates/Cross-Rates) слушает `bookticker`.
- **OrderBookPanel**: батчинг сделок раз в 200 мс, стакан до 30 уровней, цветовая группировка по цене, UTC-часы (обновление каждые 100 мс) вынесены в панель управления.
- **CrossRatesChart**: считает на клиенте `spreadA = bid₁−ask₁`, `spreadB = bid₂−ask₂`, `spreadAB = bid₁−ask₂`, `spreadBA = bid₂−ask₁`; до 60 точек, шаг 1 c.
- Прокси CRA на `http://localhost:8080` (`package.json` → `proxy`). SSE идёт напрямую на `http://localhost:8080/api`.

---

## 5. Конфигурация (`application.yml`)

```yaml
server.port: 8080
spring.datasource.url: jdbc:postgresql://localhost:5432/arbitrage_screener
spring.datasource.username: postgres
spring.datasource.password: ${DB_PASSWORD}      # нужна переменная окружения
spring.liquibase.enable: true                    # миграции выполняются при старте
symbols: BTCUSDC,ETHUSDC,SOLUSDC,BNBUSDC,XRPUSDC,TRXUSDC
management.endpoints.web.exposure.include: health, metrics
```

Блоки `arbitrage:`, `fees:`, `reconnect:`, `statistics:` **присутствуют в конфиге, но не читаются кодом** (нет соответствующих `@ConfigurationProperties`/сервисов). Значения backoff-реконнекта зашиты константами в адаптерах.

---

## 6. База данных (Liquibase)

`backend/src/main/resources/db/changelog/`:

| Файл | Содержимое |
|------|-----------|
| `db.changelog-master.yaml` | Включает 001, 002, 003 |
| `001-create-spread-history.sql` | Таблица `spread_history` |
| `002-create-arbitrage-event.sql` | Таблица `arbitrage_event` |
| `003-insert-initial-data.sql` | Seed-данные |

> ⚠️ Таблицы создаются и наполняются миграциями, но в Java-коде **нет ни одного репозитория/
> запроса** к ним. jOOQ-starter подключён, codegen и репозитории не реализованы. Фактически
> БД сейчас не участвует в работе приложения, но её наличие **обязательно для старта**
> (Liquibase + datasource), и требуется `DB_PASSWORD`.

---

## 7. Тесты

**Тестов в репозитории сейчас нет** (`backend/src/test/**` пуст). Прежний чекпоинт упоминал 100+ тестов под старую (удалённую) архитектуру — они неактуальны.

---

## 8. Как запустить

```bash
# БД (нужен PostgreSQL с базой arbitrage_screener)
export DB_PASSWORD=...

# Backend
cd backend && ./mvnw spring-boot:run          # http://localhost:8080

# Frontend
cd frontend && npm install && npm start        # http://localhost:3000 (proxy → 8080)
```

---

## 9. Известные проблемы / технический долг

1. **Несогласованность символов.** `application.yml` задаёт пары на `…USDC`, `/api/symbols`
   отдаёт их как `BTC-USDC`, но `ExchangeController.connect()` принудительно нормализует символ
   в `substring(0,3) + "USDT"`. То есть выбор `ETH-USDC` на фронте уйдёт на биржу как `ETHUSDT`.
   Нужно унифицировать нормализацию символов.
2. **Мёртвая конфигурация.** Блоки `arbitrage`, `fees`, `statistics`, `reconnect` в `application.yml`
   ничем не используются.
3. **БД не используется, но обязательна для старта.** Liquibase-миграции и seed есть, кода доступа
   нет; jOOQ-репозитории не реализованы. Приложение не поднимется без PostgreSQL и `DB_PASSWORD`.
4. **Кэши наполняются вхолостую.** `DealCache`/`OrderBookCache` пишутся `ExchangeManager`, но
   читателей нет.
5. **Нет тестов.**
6. **`ExchangeEnum.wsUrl` почти не используется** — адаптеры (по крайней мере Binance) держат
   собственные константы эндпоинтов; возможное дублирование/рассинхрон.
7. **Двойная регистрация обработчиков на адаптер** (`AbstractSseController` + `ExchangeManager`) —
   работает, но связи неявные; стоит задокументировать или централизовать.
8. **Нет Docker/контейнеризации и CI.**

---

## 10. Ключевые файлы для быстрого входа

| Файл | Зачем |
|------|-------|
| `frontend/src/services/api.ts` | Контракт фронт↔бэк (REST + SSE) |
| `frontend/src/components/OrderBookPanel.tsx` | Основная вкладка: стакан + T&S + UTC |
| `frontend/src/components/CrossRatesChart.tsx` | Логика расчёта кросс-спредов на клиенте |
| `backend/.../exchange/ExchangeAdapter.java` | Интерфейс адаптера биржи |
| `backend/.../exchange/binance/BinanceAdapter.java` | Эталонная реализация адаптера (3 WS-канала) |
| `backend/.../exchange/ExchangeManager.java` | Управление подключениями и кэшами |
| `backend/.../controller/AbstractSseController.java` | Базовый SSE-стриминг на фронт |
| `backend/.../controller/ExchangeController.java` | connect/disconnect + нормализация символа |
| `backend/src/main/resources/application.yml` | Конфигурация |
| `docs/technical-specification.md`, `docs/sprints.md` | Исходное ТЗ и план спринтов |
