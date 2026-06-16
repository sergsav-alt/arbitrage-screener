package com.arbitrage.screener.exchange.bingx;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeWebSocketClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ch.qos.logback.core.util.StringUtil.isNullOrEmpty;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;
import static com.arbitrage.screener.util.ObjectMapperHelper.writeValueAsString;

/**
 * WebSocket-адаптер для биржи BingX (спотовый рынок).
 * Подключается к wss://open-api-ws.bingx.com/market,
 * подписывается на bookTicker-потоки и нормализует в {@link Ticker}.
 */
@Slf4j
public class BingXAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final int PING_INTERVAL_SECONDS = 180;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;

    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<Ticker>> tickerHandlers;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final AtomicInteger reconnectAttempt;

    private volatile ExchangeWebSocketClient webSocketClient;
    private volatile boolean connected = false;
    private volatile ScheduledFuture<?> pingTask;
    private String subscribedSymbol;
    private String id;

    public BingXAdapter() {
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
        if (!connected) {
            doConnect();
        }
    }

    private void doConnect() {
        var targetUri = URI.create(exchange.getWsUrl());
        this.webSocketClient = new ExchangeWebSocketClient(
                targetUri,
                "BingX",
                this::onConnected,
                this::handleMessage,
                this::handleClose,
                () -> {
                    connected = false;
                    stopPing();
                    scheduleReconnect();
                }
        );
        try {
            this.webSocketClient.connect();
            this.connected = true;
        } catch (Exception e) {
            log.error("BingX: failed to connect to webSocket: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void onConnected() {
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("BingX: connected to webSocket");
        startPing();
        subscribe();
    }

    @Override
    public void disconnect() {
        log.info("BingX: disconnecting from webSocket");
        connected = false;
        stopPing();
        if (webSocketClient != null && webSocketClient.isOpen()) {
            unsubscribe();
            webSocketClient.close();
        }
    }

    @Override
    public void subscribe(String symbol) {
        if (isNullOrEmpty(symbol)) {
            log.warn("BingX: no symbols provided for subscription");
            return;
        }
        this.subscribedSymbol = symbol;
        if (connected) {
            subscribe();
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

    private void subscribe() {
        var client = this.webSocketClient;
        if (client == null || !connected) return;

        var formattedSymbol = formatSymbol(subscribedSymbol);
        var msg = buildSubscriptionMessage(formattedSymbol);
        client.send(msg);
    }

    private void unsubscribe() {
        var client = this.webSocketClient;
        if (client == null || !client.isOpen()) return;

        var formattedSymbol = formatSymbol(subscribedSymbol);
        var msg = buildUnsubscriptionMessage(formattedSymbol);
        client.send(msg);
    }

    /**
     * Форматирует символ в формат BingX: BTCUSDT → BTC-USDT.
     */
    private String formatSymbol(String symbol) {
        return symbol.toUpperCase().substring(0, 3) + "-USD";
    }

    private String buildSubscriptionMessage(String symbol) {
        this.id = UUID.randomUUID().toString();
        var node = MAPPER.createObjectNode()
                .put("id", id)
                .put("reqType", "sub")
                .put("dataType", symbol + "@bookTicker");
        return writeValueAsString(node);
    }

    private String buildUnsubscriptionMessage(String symbol) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("id", this.id)
                    .put("reqType", "unsub")
                    .put("dataType", symbol + "@bookTicker");
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("BingX: failed to build unsubscription message", e);
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
        var client = this.webSocketClient;
        if (client != null && connected) {
            client.sendPing();
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
        log.info("BingX: reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

// --- Message Handling ---

    public void handleMessage(String text) {
        try {
            var root = MAPPER.readTree(text);

            if (root.has("code") && !root.has("dataType")) {
                log.debug("BingX: received subscription confirmation: code={}", root.get("code").asInt());
                return;
            }

            var dataTypeNode = root.get("dataType");
            var dataNode = root.get("data");
            if (dataTypeNode == null || dataNode == null) {
                log.debug("BingX: skipping message without dataType/data");
                return;
            }

            var dataType = dataTypeNode.asText();
            if (dataType.contains("bookTicker")) {
                parseBookTicker(dataNode);
            }
        } catch (JsonProcessingException e) {
            log.error("BingX: failed to parse webSocket message: {}", e.getMessage());
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
        log.warn("BingX: webSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (statusCode != org.java_websocket.framing.CloseFrame.NORMAL) {
            scheduleReconnect();
        }
    }
}
