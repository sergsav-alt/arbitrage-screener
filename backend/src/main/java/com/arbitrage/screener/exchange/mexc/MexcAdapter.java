package com.arbitrage.screener.exchange.mexc;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.model.Ticker;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * WebSocket-адаптер для биржи MEXC (спотовый рынок).
 * Подключается к wss://wbs-api.mexc.com/ws,
 * подписывается на bookTicker-потоки и нормализует в {@link Ticker}.
 */
@Slf4j
public class MexcAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();
    private static final String BOOK_TICKER_CHANNEL = "spot@public.aggre.bookTicker.v3.api.pb@100ms@%s";

    private static final int PING_INTERVAL_SECONDS = 60;
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

    public MexcAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "mexc-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.MEXC;
        this.tickerHandlers = new CopyOnWriteArrayList<>();
        this.orderBookHandlers = new CopyOnWriteArrayList<>();
        this.reconnectAttempt = new AtomicInteger(0);
    }

    @Override
    public void connect() {
        if (connected) {
            log.debug("Already connected to MEXC");
            return;
        }
        log.info("Connecting to MEXC WebSocket...");
        doConnect();
    }

    private void doConnect() {
        var targetUri = URI.create(exchange.getWsUrl());
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .buildAsync(targetUri, new MexcWebSocketListener())
                .thenAccept(this::onConnected)
                .exceptionally(this::onConnectionError);
    }

    private void onConnected(WebSocket ws) {
        this.webSocket = ws;
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("Connected to MEXC WebSocket");
        startPing();
        subscribeAll();
    }

    private Void onConnectionError(Throwable error) {
        log.error("Failed to connect to MEXC: {}", error.getMessage());
        scheduleReconnect();
        return null;
    }

    @Override
    public void disconnect() {
        log.info("Disconnecting from MEXC WebSocket");
        connected = false;
        stopPing();
        if (webSocket != null && !webSocket.isOutputClosed()) {
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

        var channels = Arrays.stream(subscribedSymbols)
                .map(s -> String.format(BOOK_TICKER_CHANNEL, s.toUpperCase()))
                .toArray(String[]::new);

        var msg = buildSubscriptionMessage(channels);
        log.debug("Subscribing to MEXC channels: {}", String.join(",", channels));
        ws.sendText(msg, true);
    }

    private String buildSubscriptionMessage(String[] channels) {
        try {
            var paramsNode = MAPPER.createArrayNode();
            for (var channel : channels) {
                paramsNode.add(channel);
            }
            var node = MAPPER.createObjectNode()
                    .put("method", "SUBSCRIPTION")
                    .set("params", paramsNode);
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build subscription message", e);
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
            try {
                var pingMsg = MAPPER.writeValueAsString(
                        MAPPER.createObjectNode().put("method", "PING")
                );
                ws.sendText(pingMsg, true);
            } catch (JsonProcessingException e) {
                log.error("Failed to build ping message", e);
            }
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
        log.info("Scheduling MEXC reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    // --- Message Handling ---

    public void handleMessage(String text) {
        try {
            var root = MAPPER.readTree(text);

            if (root.has("msg") && root.has("code")) {
                log.debug("Received MEXC subscription ack: code={}", root.get("code").asInt());
                return;
            }

            if (root.has("channel") && root.has("publicbookticker")) {
                parseBookTicker(root);
            } else {
                log.debug("Skipping unrecognized MEXC message");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse MEXC WebSocket message: {}", e.getMessage());
        }
    }

    void parseBookTicker(JsonNode root) {
        var symbol = root.get("symbol").asText();
        var tickerData = root.get("publicbookticker");

        var bidPrice = new BigDecimal(tickerData.get("bidprice").asText());
        var bidQty = new BigDecimal(tickerData.get("bidquantity").asText());
        var askPrice = new BigDecimal(tickerData.get("askprice").asText());
        var askQty = new BigDecimal(tickerData.get("askquantity").asText());

        var ticker = new Ticker(exchange, symbol, bidPrice, bidQty, askPrice, askQty, Instant.now());
        tickerHandlers.forEach(h -> h.accept(ticker));

        var bids = List.of(new OrderBookLevel(bidPrice, bidQty));
        var asks = List.of(new OrderBookLevel(askPrice, askQty));
        var orderBook = new OrderBook(exchange, symbol, bids, asks, Instant.now());
        orderBookHandlers.forEach(h -> h.accept(orderBook));
    }

    public void handleClose(int statusCode, String reason) {
        log.warn("MEXC WebSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (statusCode != WebSocket.NORMAL_CLOSURE && statusCode != 1000) {
            scheduleReconnect();
        }
    }

    public void handleError(Throwable error) {
        log.error("MEXC WebSocket error: {}", error.getMessage());
        connected = false;
        stopPing();
        scheduleReconnect();
    }

    // --- WebSocket Listener ---

    private class MexcWebSocketListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            log.info("MEXC WebSocket connection opened");
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
            log.debug("Pong received from MEXC");
            return WebSocket.Listener.super.onPong(webSocket, message);
        }
    }
}
