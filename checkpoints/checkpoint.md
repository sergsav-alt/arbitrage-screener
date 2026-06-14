# Чекпоинт: Арбитражный скринер криптовалютных бирж

**Дата:** 2026-06-14
**Статус:** Спринты 1–5 завершены. Спринт 6 в процессе — созданы Liquibase-миграции с начальными данными.

---

## 1. Что это за проект

Инструмент для обнаружения кроссбиржевых арбитражных возможностей в реальном времени.
Бэкенд на Spring Boot подключается к биржам через WebSocket, получает котировки, считает спреды с учётом комиссий и ликвидности.
Фронтенд на React + Ant Design отображает найденные возможности в реальном времени через SSE.

### Стек

| Слой | Технология |
|------|-----------|
| Бэкенд | Java 21, Spring Boot 4.1.0, Maven, Lombok, Jackson, jOOQ |
| WS-клиенты | `java.net.http.WebSocket` (нативные, без сторонних библиотек) |
| Фронтенд | React 19, TypeScript 4.9, Ant Design 6, Axios, react-scripts (CRA) |
| БД | PostgreSQL + jOOQ + Liquibase (миграции) |

### Биржи

| Биржа | Статус адаптера | URL WebSocket |
|-------|----------------|---------------|
| BingX | ✅ Готов | `wss://open-api-ws.bingx.com/market` |
| MEXC | ✅ Готов | `wss://wbs-api.mexc.com/ws` |

---

## 2. Текущая архитектура (что уже реализовано)

### Структура пакетов бэкенда

```
com.arbitrage.screener
├── BackendApplication.java              # @SpringBootApplication, @EnableScheduling
├── config/
│   ├── CoreConfig.java                  # Все бины ядра (Spring-конфигурация)
│   ├── FeeConfig.java                   # @ConfigurationProperties("fees"): taker-percent, withdrawal
│   └── CorsConfig.java                  # CORS для localhost:3000
├── entity/
│   ├── Ticker.java                      # record: exchange, symbol, bidPrice/askPrice, bidQty/askQty, timestamp
│   ├── TickerCache.java                 # Потокобезопасный кэш (ConcurrentHashMap), TTL=30s
│   ├── OrderBook.java                   # record: exchange, symbol, bids, asks, timestamp
│   ├── OrderBookLevel.java              # record: price, quantity
│   ├── ArbitrageOpportunity.java        # record: id, symbol, buyExchange, sellExchange, buyPrice, sellPrice,
│   │                                    #          spreadPercent, netProfit, maxVolume, slippage, type, detectedAt
│   └── ArbitrageType.java               # enum: INTRA_EXCHANGE, CROSS_EXCHANGE
├── exchange/
│   ├── ExchangeAdapter.java             # Интерфейс: connect(), disconnect(), subscribe(), onTicker(), onOrderBook()
│   ├── ExchangeAdapterFactory.java      # Фабрика + кэш адаптеров по ExchangeEnum
│   ├── ExchangeEnum.java                # BINGX, MEXC (с WS URL)
│   ├── PriceNormalizer.java             # Группировка тикеров по нормализованному символу
│   ├── bingx/
│   │   └── BingXAdapter.java            # WS: JSON-подписки, bookTicker + depth, reconnect
│   └── mexc/
│       └── MexcAdapter.java             # WS: JSON-подписки, bookTicker + depth, reconnect
├── core/
│   ├── SpreadCalculator.java            # Расчёт спреда: % и абсолютный, макс. объём, проверка прибыльности
│   ├── FeeCalculator.java               # Расчёт taker-комиссии (volume * rate% / 100)
│   ├── WithdrawalFeeCalculator.java     # Расчёт комиссии на вывод (по символу и бирже)
│   ├── OrderBookCache.java              # Потокобезопасный кэш стаканов (TTL=30s)
│   ├── LiquidityChecker.java            # Проверка достаточности объёма в стакане
│   ├── SlippageCalculator.java          # Оценка проскальзывания (средневзвешенная цена)
│   ├── VolumeLimiter.java               # Фильтрация по minVolume из конфига
│   ├── ArbitrageDetector.java           # Сравнение пар бирж, расчёт netProfit с учётом всех издержек
│   ├── OpportunityRepository.java       # Хранение возможностей (ConcurrentHashMap)
│   ├── ArbitrageService.java            # Оркестратор: scan() → detect → save
│   ├── ArbitrageScheduler.java          # @Scheduled запуск scan(), @PostConstruct/@PreDestroy
│   └── ExchangeManager.java             # Управление адаптерами, маршрутизация тикеров и стаканов в кэши
└── controller/
    ├── ArbitrageController.java         # REST: /api/opportunities, /api/exchanges, /api/symbols
    ├── StreamController.java            # SSE: /api/stream/opportunities
    └── GlobalExceptionHandler.java      # @RestControllerAdvice
```

### Ключевые архитектурные решения

1. **Adapter Pattern** — каждый адаптер биржи изолирован за `ExchangeAdapter` (OCP из SOLID).
2. **Observer через Consumer** — уведомления через `Consumer<Ticker>` / `Consumer<OrderBook>`.
3. **Records (Java 17)** — все DTO иммутабельны.
4. **BigDecimal для денег** — никаких `double`/`float`.
5. **SSE для real-time** — `SseEmitter` для стриминга на фронтенд.
6. **Автопереподключение** — экспоненциальный backoff (1→2→4→...→60 сек).
7. **Расчёт чистой прибыли** — с учётом taker-комиссий обеих бирж, комиссии на вывод и проскальзывания.

### Пайплайн арбитража (ArbitrageService.scan())

```
Ticker/OrderBook (от адаптера)
  → TickerCache.put(ticker) + OrderBookCache.put(orderBook)
  → tickerCache.evictExpired() + orderBookCache.evictExpired()
  → PriceNormalizer.groupBySymbol()
  → ArbitrageDetector.detect()
      ├── SpreadCalculator: gross spread
      ├── FeeCalculator: taker fee × 2 биржи
      ├── WithdrawalFeeCalculator: фиксированная комиссия вывода
      ├── LiquidityChecker: достаточно ли объёма в стакане
      ├── SlippageCalculator: средневзвешенная цена по уровням
      ├── VolumeLimiter: фильтр по minTradeVolume
      └── netProfit = grossProfit - fees - withdrawalFee - slippage
  → OpportunityRepository.save()
```

---

## 3. Текущее состояние фронтенда

- **React 19 + TypeScript + Ant Design 6**
- Компоненты: `AppLayout`, `OpportunitiesTable`, `Filters`, `ExchangeStatus`
- REST-запросы через Axios (+ SSE через EventSource)
- Таблица с сортировкой, цветовой индикацией спреда и netProfit, колонкой slippage
- Фильтры: symbol, buy/sell exchange, min spread
- Индикаторы статуса бирж (опрос каждые 5 сек)

### Структура фронтенда

```
frontend/src/
├── App.tsx                              # Состояние, фильтрация, SSE-подписка
├── components/
│   ├── AppLayout.tsx                    # Layout: Header + Sider + Content
│   ├── OpportunitiesTable.tsx           # Ant Table: колонки + slippage
│   ├── Filters.tsx                      # Select'ы + Slider minSpread
│   └── ExchangeStatus.tsx               # Badge индикаторы подключения
├── services/
│   └── api.ts                           # REST + SSE клиент
└── types/
    └── index.ts                         # ArbitrageOpportunity, ExchangeStatus, OpportunityFilters
```

---

## 4. Что предстоит (оставшиеся спринты)

### Спринт 6: Исторические данные, статистика и графики — **В ПРОЦЕССЕ**
- ✅ PostgreSQL + jOOQ + Liquibase миграции (001, 002, 003)
- ✅ Начальные данные (seed) для spread_history и arbitrage_event
- ⬜ `StatisticsCollector`, эндпоинты `/api/statistics`
- ⬜ Recharts на фронтенде: дашборд и графики спреда
- ⬜ Реализовать репозитории jOOQ (ArbitrageEventRepositoryImpl, SpreadHistoryRepositoryImpl)

### Спринт 7: Продвинутые функции и контейнеризация
- Triangular arbitrage, уведомления, экспорт CSV/JSON, Docker

---

## 5. Liquibase-миграции и seed-данные (Добавлено 2026-06-14)

### Файлы в `backend/src/main/resources/db/changelog/`

| Файл | Описание |
|------|----------|
| `db.changelog-master.yaml` | Включает 001, 002, 003 (последовательно) |
| `001-create-spread-history.sql` | Таблица `spread_history` (история спредов: symbol, buy/sell exchange, цены, spread%, netProfit, maxVolume, recorded_at) |
| `002-create-arbitrage-event.sql` | Таблица `arbitrage_event` (арбитражные события: type (OPEN/CLOSED/MISSED/REJECTED), те же поля + slippage, detected_at) |
| `003-insert-initial-data.sql` | 19 записей в spread_history + 10 записей в arbitrage_event с реалистичными данными |

### Начальные данные

**spread_history** — 19 записей по парам BTC/USDT, ETH/USDT, SOL/USDT, XRP/USDT, DOGE/USDT, LTC/USDT на биржах Binance, Bybit, OKX с `recorded_at` от 10:15 до 14:45.

**arbitrage_event** — 10 записей:
- `CLOSED`: 3 завершённые сделки (с slippage > 0)
- `OPEN`: 4 активные возможности (slippage = 0)
- `MISSED`: 2 пропущенные
- `REJECTED`: 1 отклонённая

### Деталь, которую важно помнить

При создании 003-файла сначала забыли указать `recorded_at` в INSERT для `spread_history`. Несмотря на `DEFAULT NOW()` в схеме, это вызывало ошибку not-null constraint. **Исправлено** — `recorded_at` добавлен явно с таймстемпами, равномерно распределёнными по дню.

### Репозитории jOOQ (уже созданы но не реализованы)

- `ArbitrageEventRepositoryImpl.java` — заглушка
- `SpreadHistoryRepositoryImpl.java` — заглушка

---

## 6. Тесты

**Всего: 100+ тестов, 0 failures.** Запуск: `cd backend && mvn test`

| Группа тестов | Количество | Что проверяет |
|--------------|-----------|---------------|
| `BingXAdapterTest` | 12 | Парсинг bookTicker, нормализация, подписки |
| `MexcAdapterTest` | 8 | Парсинг publicbookticker, обработчики, ack |
| `ExchangeAdapterFactoryTest` | 5 | Создание адаптеров, кэширование, уникальность |
| `PriceNormalizerTest` | 6 | Группировка, нормализация символов |
| `ExchangeIntegrationTest` | 6 | Сквозной сценарий с биржами + общий кэш |
| `TickerCacheTest` | 10 | CRUD, TTL, evictExpired, getFresh |
| `OrderBookCacheTest` | 6 | CRUD, TTL, evictExpired |
| `SpreadCalculatorTest` | 13 | Позитивный/негативный/нулевой спред, макс. объём |
| `FeeCalculatorTest` | 4 | Taker-комиссия для разных объёмов и бирж |
| `WithdrawalFeeCalculatorTest` | 5 | Комиссия вывода по символам и биржам |
| `LiquidityCheckerTest` | 5 | Достаточность/недостаточность ликвидности |
| `SlippageCalculatorTest` | 6 | Проскальзывание на одном/нескольких уровнях, % |
| `VolumeLimiterTest` | 5 | Фильтрация по minVolume |
| `ArbitrageDetectorTest` | 7 | Детекция с учётом комиссий и ликвидности |
| `ArbitragePipelineTest` | 7 | Полный пайплайн с fees и liquidity |

---

## 7. Конфигурация (application.yml)

```yaml
symbols: BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, XRPUSDT, ADAUSDT, DOGEUSDT
exchanges:
  bingx: enabled: true, ws-url: wss://open-api-ws.bingx.com/market
  mexc: enabled: true, ws-url: wss://wbs-api.mexc.com/ws
arbitrage:
  min-spread-percent: 0.1
  ttl-seconds: 30
  scan-interval-ms: 1000
  default-trade-volume-usdt: 100
  min-trade-volume-usdt: 10
fees:
  taker-percent: bingx: 0.1%, mexc: 0.1%
  withdrawal: per-symbol: USDT (bingx: $1, mexc: $2), default-usdt: $2
reconnect: initial-delay: 1s, max-delay: 60s, ping-interval: 180s
```

---

## 8. Ключевые файлы для быстрого входа

| Файл | Зачем читать |
|------|-------------|
| `docs/technical-specification.md` | Полное ТЗ |
| `docs/sprints.md` | План всех 7 спринтов |
| `backend/src/main/resources/application.yml` | Текущая конфигурация (включая fees) |
| `backend/src/main/resources/db/changelog/` | Liquibase-миграции и seed-данные |
| `backend/src/main/java/.../config/CoreConfig.java` | Все Spring-бины |
| `backend/src/main/java/.../core/ArbitrageDetector.java` | Ядро расчёта с fees/liquidity/slippage |
| `backend/src/main/java/.../config/FeeConfig.java` | Конфигурация комиссий |
| `backend/src/main/java/.../exchange/ExchangeAdapter.java` | Интерфейс адаптера биржи |
| `backend/src/main/java/.../entity/ArbitrageEvent.java` | JPA entity для arbitrage_event |
| `backend/src/main/java/.../entity/SpreadHistory.java` | JPA entity для spread_history |

---

## 9. Известные TODO / Технический долг

- **Реализовать jOOQ репозитории** — `ArbitrageEventRepositoryImpl` и `SpreadHistoryRepositoryImpl` созданы как заглушки, нужно наполнить jOOQ-запросами.
- **Дублирование URL бирж** — `ExchangeEnum.getWsUrl()` и `application.yml` дублируют URL.
- **OrderBook depth** — используется bookTicker (1 уровень стакана), полноценный depth20 не реализован.
- **Только кроссбиржевой арбитраж** — `ArbitrageType.INTRA_EXCHANGE` определён, но не реализован (Спринт 7).
- **`JooqTables.java`** — сгенерирован, но не используется. Возможно, конфигурация jOOQ codegen требует доработки.
