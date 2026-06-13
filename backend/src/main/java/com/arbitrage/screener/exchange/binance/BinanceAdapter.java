package com.arbitrage.screener.exchange.binance;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WebSocket-адаптер для биржи Binance.
 * Подключается к комбинированному потоку (combined streams),
 * парсит miniTicker и bookTicker, нормализует в {@link Ticker}.
 */
@Slf4j
public class BinanceAdapter implements ExchangeAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STREAMS_PATH = "/stream?streams=";

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
    private String subscribedStreams;

    public BinanceAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
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
        if (connected) {
            log.debug("Already connected to Binance");
            return;
        }
        log.info("Connecting to Binance WebSocket...");
        doConnect();
    }

    private void doConnect() {
        var targetUri = URI.create(exchange.getWsUrl() + STREAMS_PATH + subscribedStreams);
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .buildAsync(targetUri, new BinanceWebSocketListener())
                .thenAccept(this::onConnected)
                .exceptionally(this::onConnectionError);
    }

    private void onConnected(WebSocket ws) {
        this.webSocket = ws;
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("Connected to Binance WebSocket");
        startPing();
    }

    private Void onConnectionError(Throwable error) {
        log.error("Failed to connect to Binance: {}", error.getMessage());
        scheduleReconnect();
        return null;
    }

    @Override
    public void disconnect() {
        log.info("Disconnecting from Binance WebSocket");
        connected = false;
        stopPing();
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting");
        }
    }

    @Override
    public void subscribe(String... streams) {
        if (streams.length == 0) {
            log.warn("No streams provided for subscription");
            return;
        }
        this.subscribedStreams = String.join("/", streams);
        log.info("Subscribed to streams: {}", subscribedStreams);
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

    // --- Ping / Heartbeat ---

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
        var delay = Math.min(INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1), MAX_RECONNECT_DELAY_SECONDS);
        log.info("Scheduling reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    // --- Message Parsing ---

    void handleMessage(String text) {
        try {
            var root = OBJECT_MAPPER.readTree(text);
            var streamNode = root.get("stream");
            if (streamNode != null) {
                parseCombinedStream(streamNode.asText(), root.get("data"));
            } else {
                log.debug("Skipping non-combined message");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    void handleClose(int statusCode, String reason) {
        log.warn("WebSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (statusCode != WebSocket.NORMAL_CLOSURE && statusCode != 1000) {
            scheduleReconnect();
        }
    }

    void handleError(Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
        connected = false;
        stopPing();
        scheduleReconnect();
    }

    private void parseCombinedStream(String stream, JsonNode data) {
        if (data == null) return;
        if (stream.contains("miniTicker")) {
            parseMiniTicker(data);
        } else if (stream.contains("bookTicker")) {
            parseBookTicker(data);
        }
    }

    void parseMiniTicker(JsonNode data) {
        var symbol = data.get("s").asText();
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());

        var ticker = new Ticker(exchange, symbol, bidPrice, bidQty, askPrice, askQty, Instant.now());
        tickerHandlers.forEach(handler -> handler.accept(ticker));
    }

    void parseBookTicker(JsonNode data) {
        var symbol = data.get("s").asText();
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());

        var ticker = new Ticker(exchange, symbol, bidPrice, bidQty, askPrice, askQty, Instant.now());
        tickerHandlers.forEach(handler -> handler.accept(ticker));

        var bids = List.of(new OrderBookLevel(bidPrice, bidQty));
        var asks = List.of(new OrderBookLevel(askPrice, askQty));
        var orderBook = new OrderBook(exchange, symbol, bids, asks, Instant.now());
        orderBookHandlers.forEach(handler -> handler.accept(orderBook));
    }

    // --- WebSocket Listener ---

    private class BinanceWebSocketListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            log.info("Binance WebSocket connection opened");
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
            log.debug("Pong received from Binance");
            return WebSocket.Listener.super.onPong(webSocket, message);
        }
    }
}
