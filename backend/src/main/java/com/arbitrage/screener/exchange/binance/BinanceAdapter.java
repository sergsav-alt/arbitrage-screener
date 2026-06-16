package com.arbitrage.screener.exchange.binance;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeWebSocketClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ch.qos.logback.core.util.StringUtil.isNullOrEmpty;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * WebSocket-адаптер для биржи Binance (USDⓈ-M Futures).
 * Подключается к wss://fstream.binance.com/stream,
 * использует комбинированный режим потоков (stream?streams=...)
 * и нормализует bookTicker в {@link Ticker}.
 */
@Slf4j
public class BinanceAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;

    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<Ticker>> tickerHandlers;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final AtomicInteger reconnectAttempt;

    private volatile ExchangeWebSocketClient webSocketClient;
    private volatile boolean connected = false;
    private volatile boolean closing;
    private volatile String subscribedSymbol;

    public BinanceAdapter() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "binance-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.BINANCE;
        this.tickerHandlers = new CopyOnWriteArrayList<>();
        this.orderBookHandlers = new CopyOnWriteArrayList<>();
        this.reconnectAttempt = new AtomicInteger(0);
    }

    @Override
    public void connect() {
        if (!connected) {
            doConnect();
        }
    }

    private void doConnect() {
        var targetUri = buildStreamUri();
        this.webSocketClient = new ExchangeWebSocketClient(
                targetUri,
                "Binance",
                this::onConnected,
                this::handleMessage,
                this::handleClose,
                () -> {
                    connected = false;
                    scheduleReconnect();
                }
        );
        try {
            this.webSocketClient.connect();
            this.connected = true;
        } catch (Exception e) {
            log.error("Binance: failed to connect: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * Строит URI с потоками в query-параметре.
     * Пример: wss://fstream.binance.com/stream?streams=btcusdt@bookTicker/ethusdt@bookTicker
     */
    private URI buildStreamUri() {
        if (isNullOrEmpty(subscribedSymbol)) {
            return URI.create(exchange.getWsUrl());
        }
        return URI.create(exchange.getWsUrl() + "?streams=" + formatSymbol(subscribedSymbol) + "@bookTicker/");
    }

    private void onConnected() {
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("Binance: connected to WebSocket");
    }

    @Override
    public void disconnect() {
        log.info("Binance: disconnecting from WebSocket");
        closing = true;
        connected = false;
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
    }

    @Override
    public void subscribe(String symbol) {
        if (isNullOrEmpty(symbol)) {
            log.warn("Binance: no symbols provided for subscription");
            return;
        }
        this.subscribedSymbol = symbol;
        if (connected) {
            disconnect();
            closing = false;
            doConnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public ExchangeEnum getExchange() {
        return exchange;
    }

    @Override
    public void onTicker(Consumer<Ticker> handler) {
        tickerHandlers.add(handler);
    }

    @Override
    public void onOrderBook(Consumer<OrderBook> handler) {
        orderBookHandlers.add(handler);
    }

    @Override
    public void clearHandlers() {
        tickerHandlers.clear();
        orderBookHandlers.clear();
    }

    // --- Reconnection ---

    private void scheduleReconnect() {
        disconnect();
        var attempt = reconnectAttempt.incrementAndGet();
        var delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1),
                MAX_RECONNECT_DELAY_SECONDS
        );
        log.info("Binance: reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    // --- Message Handling ---

    void handleMessage(String text) {
        try {
            var root = MAPPER.readTree(text);

            var streamNode = root.get("stream");
            var dataNode = root.get("data");

            if (streamNode == null || dataNode == null) {
                log.debug("Binance: unexpected message format: {}", text);
                return;
            }

            var stream = streamNode.asText();
            if (stream.contains("@bookTicker")) {
                parseBookTicker(dataNode);
            } else {
                log.debug("Binance: unhandled stream: {}", stream);
            }
        } catch (Exception e) {
            log.error("Binance: failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    void parseBookTicker(JsonNode data) {
        var symbol = normalizeSymbol(data.get("s").asText());
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());

        var ticker = new Ticker(exchange, symbol, bidPrice, bidQty, askPrice, askQty, Instant.now());
        tickerHandlers.forEach(h -> h.accept(ticker));

        var bids = List.of(new OrderBookLevel(bidPrice, bidQty));
        var asks = List.of(new OrderBookLevel(askPrice, askQty));
        var orderBook = new OrderBook(exchange, symbol, bids, asks, Instant.now());
        orderBookHandlers.forEach(h -> h.accept(orderBook));
    }

    void handleClose(int statusCode, String reason) {
        log.warn("Binance: WebSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        if (!closing) {
            scheduleReconnect();
        }
    }

    /**
     * Форматирует символ в формат Binance: BTCUSDT → btcusdt.
     */
    private String formatSymbol(String symbol) {
        return symbol.toLowerCase().substring(0, 3) + "usdt";
    }

    /**
     * Нормализует символ из формата Binance (btcusdt) в общий (BTCUSDT).
     */
    private String normalizeSymbol(String symbol) {
        return symbol.toUpperCase();
    }
}
