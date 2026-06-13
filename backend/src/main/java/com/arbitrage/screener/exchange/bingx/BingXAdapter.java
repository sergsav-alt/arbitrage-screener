package com.arbitrage.screener.exchange.bingx;

import com.arbitrage.screener.entity.OrderBook;
import com.arbitrage.screener.entity.OrderBookLevel;
import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WebSocket-адаптер для биржи BingX (спотовый рынок).
 * Подключается к wss://open-api-ws.bingx.com/market,
 * подписывается на bookTicker-потоки и нормализует в {@link Ticker}.
 */
@Slf4j
public class BingXAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int PING_INTERVAL_SECONDS = 180;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<Ticker>> tickerHandlers;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final AtomicInteger reconnectAttempt;

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile ScheduledFuture<?> pingTask;
    private String[] subscribedSymbols;

    public BingXAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "bingx-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.BINGX;
        this.tickerHandlers = new CopyOnWriteArrayList<>();
        this.orderBookHandlers = new CopyOnWriteArrayList<>();
        this.reconnectAttempt = new AtomicInteger(0);
    }

    @Override
    public void connect() {
        if (connected) {
            log.debug("Already connected to BingX");
            return;
        }
        log.info("Connecting to BingX WebSocket...");
        doConnect();
    }

    private void doConnect() {
        var targetUri = URI.create(exchange.getWsUrl());
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .buildAsync(targetUri, new BingXWebSocketListener())
                .thenAccept(this::onConnected)
                .exceptionally(this::onConnectionError);
    }

    private void onConnected(WebSocket ws) {
        this.webSocket = ws;
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("Connected to BingX WebSocket");
        startPing();
        subscribeAll();
    }

    private Void onConnectionError(Throwable error) {
        log.error("Failed to connect to BingX: {}", error.getMessage());
        scheduleReconnect();
        return null;
    }

    @Override
    public void disconnect() {
        log.info("Disconnecting from BingX WebSocket");
        connected = false;
        stopPing();
        if (webSocket != null && !webSocket.isOutputClosed()) {
            unsubscribeAll();
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
        }
    }

    @Override
    public void subscribe(String... symbols) {
        if (symbols.length == 0) {
            log.warn("No symbols provided for subscription");
            return;
        }
        this.subscribedSymbols = symbols;
        log.info("Queued subscription for {} symbols: {}", symbols.length, String.join(",", symbols));
        if (connected) {
            subscribeAll();
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

    // --- Subscription ---

    private void subscribeAll() {
        if (subscribedSymbols == null || subscribedSymbols.length == 0) return;
        var ws = this.webSocket;
        if (ws == null || !connected) return;

        for (var symbol : subscribedSymbols) {
            var formattedSymbol = formatSymbol(symbol);
            var msg = buildSubscriptionMessage(formattedSymbol);
            log.debug("Subscribing to {}", formattedSymbol);
            ws.sendText(msg, true);
        }
    }

    private void unsubscribeAll() {
        if (subscribedSymbols == null || subscribedSymbols.length == 0) return;
        var ws = this.webSocket;
        if (ws == null || ws.isOutputClosed()) return;

        for (var symbol : subscribedSymbols) {
            var formattedSymbol = formatSymbol(symbol);
            var msg = buildUnsubscriptionMessage(formattedSymbol);
            ws.sendText(msg, true);
        }
    }

    /**
     * Форматирует символ в формат BingX: BTCUSDT → BTC-USDT.
     */
    private String formatSymbol(String symbol) {
        var upper = symbol.toUpperCase();
        if (upper.contains("-")) return upper;
        if (upper.endsWith("USDT")) {
            return upper.substring(0, upper.length() - 4) + "-USDT";
        }
        return upper;
    }

    private String buildSubscriptionMessage(String symbol) {
        var id = UUID.randomUUID().toString();
        try {
            var node = MAPPER.createObjectNode()
                    .put("id", id)
                    .put("dataType", symbol + "@bookTicker");
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build subscription message", e);
        }
    }

    private String buildUnsubscriptionMessage(String symbol) {
        var id = UUID.randomUUID().toString();
        try {
            var node = MAPPER.createObjectNode()
                    .put("id", id)
                    .put("reqType", "unsub")
                    .put("dataType", symbol + "@bookTicker");
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build unsubscription message", e);
        }
    }

    // --- Ping ---

    private void startPing() {
        pingTask = scheduler.scheduleAtFixedRate(
                this::sendPing,
                PING_INTERVAL_SECONDS,
                PING_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    private void sendPing() {
        var ws = this.webSocket;
        if (ws != null && connected) {
            ws.sendPing(java.nio.ByteBuffer.allocate(0));
        }
    }

    // --- Reconnection ---

    private void scheduleReconnect() {
        disconnect();
        var attempt = reconnectAttempt.incrementAndGet();
        var delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1),
                MAX_RECONNECT_DELAY_SECONDS
        );
        log.info("Scheduling BingX reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    // --- Message Handling ---

    public void handleMessage(String text) {
        try {
            var root = MAPPER.readTree(text);

            if (root.has("code") && !root.has("dataType")) {
                log.debug("Received subscription confirmation: code={}", root.get("code").asInt());
                return;
            }

            var dataTypeNode = root.get("dataType");
            var dataNode = root.get("data");
            if (dataTypeNode == null || dataNode == null) {
                log.debug("Skipping message without dataType/data");
                return;
            }

            var dataType = dataTypeNode.asText();
            if (dataType.contains("bookTicker")) {
                parseBookTicker(dataNode);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse BingX WebSocket message: {}", e.getMessage());
        }
    }

    void parseBookTicker(JsonNode data) {
        var symbol = data.get("s").asText();
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());

        var ticker = new Ticker(exchange, normalizeSymbol(symbol), bidPrice, bidQty, askPrice, askQty, Instant.now());
        tickerHandlers.forEach(h -> h.accept(ticker));

        var bids = List.of(new OrderBookLevel(bidPrice, bidQty));
        var asks = List.of(new OrderBookLevel(askPrice, askQty));
        var orderBook = new OrderBook(exchange, normalizeSymbol(symbol), bids, asks, Instant.now());
        orderBookHandlers.forEach(h -> h.accept(orderBook));
    }

    /**
     * Нормализует символ из формата BingX (BTC-USDT) в общий (BTCUSDT).
     */
    private String normalizeSymbol(String symbol) {
        return symbol.replace("-", "").toUpperCase();
    }

    public void handleClose(int statusCode, String reason) {
        log.warn("BingX WebSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (statusCode != WebSocket.NORMAL_CLOSURE && statusCode != 1000) {
            scheduleReconnect();
        }
    }

    public void handleError(Throwable error) {
        log.error("BingX WebSocket error: {}", error.getMessage());
        connected = false;
        stopPing();
        scheduleReconnect();
    }

    // --- WebSocket Listener ---

    private class BingXWebSocketListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            log.info("BingX WebSocket connection opened");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                var message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            buffer.setLength(0);
            handleClose(statusCode, reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            handleError(error);
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
            log.debug("Pong received from BingX");
            return WebSocket.Listener.super.onPong(webSocket, message);
        }
    }
}
