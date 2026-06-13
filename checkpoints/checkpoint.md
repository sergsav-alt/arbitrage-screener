# Чекпоинт: Арбитражный скринер криптовалютных бирж

**Дата:** 2026-06-13  
**Статус:** Спринты 1–4 завершены. Готовность к Спринту 5 (учёт комиссий и ликвидности).

---

## 1. Что это за проект

Инструмент для обнаружения кроссбиржевых арбитражных возможностей в реальном времени.  
Бэкенд на Spring Boot подключается к биржам через WebSocket, получает котировки, считает спреды.  
Фронтенд на React + Ant Design отображает найденные возможности в реальном времени через SSE.

### Стек

| Слой | Технология |
|------|-----------|
| Бэкенд | Java 17, Spring Boot 4.1.0, Maven, Lombok, Jackson |
| WS-клиенты | `java.net.http.WebSocket` (нативные, без сторонних библиотек) |
| Фронтенд | React 19, TypeScript 4.9, Ant Design 6, Axios, react-scripts (CRA) |
| БД | PostgreSQL (запланирована на Спринт 6, пока не подключена) |

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
├── BackendApplication.java              # @SpringBootApplication
├── config/
│   └── CoreConfig.java                  # Все бины (Spring-конфигурация)
├── entity/
│   ├── Ticker.java                      # record: exchange, symbol, bidPrice/askPrice, bidQty/askQty, timestamp
│   ├── TickerCache.java                 # Потокобезопасный кэш (ConcurrentHashMap), TTL=30s, evictExpired()
│   ├── OrderBook.java                   # record: exchange, symbol, bids, asks, timestamp
│   ├── OrderBookLevel.java              # record: price, quantity
│   ├── ArbitrageOpportunity.java        # record: id, symbol, buyExchange, sellExchange, buyPrice, sellPrice,
│   │                                    #          spreadPercent, netProfit, maxVolume, slippage, type, detectedAt
│   └── ArbitrageType.java               # enum: INTRA_EXCHANGE, CROSS_EXCHANGE
├── exchange/
│   ├── ExchangeAdapter.java             # Интерфейс: connect(), disconnect(), subscribe(), onTicker(), onOrderBook()
│   ├── ExchangeAdapterFactory.java      # Фабрика + кэш адаптеров по ExchangeEnum
│   ├── ExchangeEnum.java                # BINGX, MEXC (с WS URL)
│   ├── PriceNormalizer.java             # Группировка тикеров по нормализованному символу (BTCUSDT)
│   ├── bingx/
│   │   └── BingXAdapter.java            # WS: JSON-подписки ("dataType":"BTC-USDT@bookTicker")
│   └── mexc/
│       └── MexcAdapter.java             # WS: JSON-подписки (method:SUBSCRIPTION, bookTicker)
├── core/
│   ├── SpreadCalculator.java            # Расчёт спреда: % и абсолютный, макс. объём, проверка прибыльности
│   ├── ArbitrageDetector.java           # Сравнение всех пар бирж, обнаружение кроссбиржевых возможностей
│   ├── OpportunityRepository.java       # Хранение возможностей (ConcurrentHashMap), уникальность по symbol:buy:sell
│   ├── ArbitrageService.java            # Оркестратор: scan() = evictExpired → groupBySymbol → detect → save
│   └── ExchangeManager.java             # Управление адаптерами: start/stop/status
└── controller/
  
```

### Ключевые архитектурные решения

1. **Adapter Pattern** — каждый адаптер биржи изолирован за интерфейсом `ExchangeAdapter` (OCP из SOLID).
2. **Observer через Consumer** — адаптеры уведомляют подписчиков через `Consumer<Ticker>` (не Spring Events).
3. **Records (Java 17)** — все DTO иммутабельны: `Ticker`, `OrderBook`, `ArbitrageOpportunity`.
4. **BigDecimal для денег** — никаких `double`/`float`.
5. **SSE для real-time** — `SseEmitter` для стриминга на фронтенд (в WebSocketTestController).
6. **Автопереподключение** — экспоненциальный backoff (1→2→4→...→60 сек) во всех адаптерах.
7. **Ping/Pong** — WebSocket-уровень (BingX) или application-уровень (MEXC: `{"method":"PING"}`).

### Форматы подписок на биржах

```
BingX:    JSON-сообщение  → {"id":"uuid","dataType":"BTC-USDT@bookTicker"}
MEXC:     JSON-сообщение  → {"method":"SUBSCRIPTION","params":["spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT",...]}
```

### Пайплайн арбитража (реализован в ArbitrageService.scan())

```
Ticker (от адаптера)
  → TickerCache.put(ticker)
  → TickerCache.evictExpired()         // удаление тикеров старше TTL=30s
  → PriceNormalizer.groupBySymbol()    // группировка: BTCUSDT → {BINGX: ticker}
  → ArbitrageDetector.detect()         // все пары бирж × оба направления
  → OpportunityRepository.save()       // ключ = symbol:buy:sell (обновление при повторе)
```

---

## 3. Текущее состояние фронтенда

Фронтенд — **бойлерплейт CRA** (`create-react-app`).  
`App.tsx` содержит стандартную заглушку React.  
Никакой кастомный UI ещё не реализован.  
UI-библиотека (Ant Design / Material UI) не установлена.

---

## 4. Что предстоит (оставшиеся спринты)

### Спринт 4: REST API, SSE и базовый фронтенд (MVP) — **СЛЕДУЮЩИЙ**
- REST-эндпоинты: `/api/opportunities`, `/api/exchanges`, `/api/symbols`, `/api/stream/opportunities` (SSE)
- CORS для `localhost:3000`
- Actuator health/metrics
- React: таблица возможностей, фильтры, SSE-подписка, индикаторы статуса бирж
- **Это ключевой спринт — после него будет работающий MVP**

### Спринт 5: Учёт комиссий и ликвидности
- `FeeCalculator`, `WithdrawalFeeCalculator`, `LiquidityChecker`, `SlippageCalculator`, `VolumeLimiter`
- Интеграция в `ArbitrageDetector` для пересчёта `netProfit`
- Подписка на depth-потоки (стакан заявок)

### Спринт 6: Исторические данные, статистика и графики
- PostgreSQL + Spring Data JPA
- `StatisticsCollector`, эндпоинты `/api/statistics`
- Recharts на фронтенде: графики спреда

### Спринт 7: Продвинутые функции и контейнеризация
- Triangular arbitrage, уведомления, экспорт CSV/JSON, Docker

---

## 5. Тесты

**Всего: 81 тест, 0 failures.** Запуск: `cd backend && mvn test`

| Группа тестов | Количество | Что проверяет |
|--------------|-----------|---------------|
| `BingXAdapterTest` | 12 | Парсинг bookTicker, нормализация символов, подписки |
| `MexcAdapterTest` | 8 | Парсинг publicbookticker, обработчики, ack-сообщения |
| `ExchangeAdapterFactoryTest` | 5 | Создание адаптеров, кэширование, уникальность |
| `PriceNormalizerTest` | 6 | Группировка, нормализация BTC-USDT→BTCUSDT |
| `ExchangeIntegrationTest` | 6 | Сквозной сценарий с 3 биржами + общий кэш |
| `TickerCacheTest` | 10 | CRUD, TTL, evictExpired(), getFresh() |
| `SpreadCalculatorTest` | 13 | Позитивный/негативный/нулевой спред, макс. объём, параметризация |
| `ArbitrageDetectorTest` | 7 | 1/2/3 биржи, убыточные сценарии, множественные символы |
| `ArbitragePipelineTest` | 7 | Полный пайплайн: ticker→cache→normalize→detect→repository |

---

## 6. Конфигурация (application.yml)

```yaml
symbols: BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, XRPUSDT, ADAUSDT, DOGEUSDT
exchanges: bingx (false), mexc (false)
arbitrage: min-spread-percent: 0.1, ttl-seconds: 30, scan-interval-ms: 1000
reconnect: initial-delay: 1s, max-delay: 60s, ping-interval: 180s
```

---

## 7. Ключевые файлы для быстрого входа

| Файл | Зачем читать |
|------|-------------|
| `docs/technical-specification.md` | Полное ТЗ (351 строка) |
| `docs/sprints.md` | План всех 7 спринтов |
| `backend/src/main/resources/application.yml` | Текущая конфигурация |
| `backend/src/main/java/.../config/CoreConfig.java` | Все Spring-бины (точка входа в граф зависимостей) |
| `backend/src/main/java/.../core/ArbitrageService.java` | Оркестратор арбитража |
| `backend/src/main/java/.../exchange/ExchangeAdapter.java` | Интерфейс адаптера биржи |

---

## 8. Известные TODO / Технический долг

- **Дублирование URL бирж** — `ExchangeEnum.getWsUrl()` и `application.yml` дублируют URL. Нужно вынести в конфиг.
- **Фронтенд** — полностью boilerplate. Ждёт Спринта 4.
- **`netProfit` и `slippage`** — сейчас `netProfit = absoluteSpread` (без учёта комиссий), `slippage = 0`. Будет исправлено в Спринте 5.
- **Только кроссбиржевой арбитраж** — `ArbitrageType.INTRA_EXCHANGE` определён, но не реализован (Спринт 7).
