package com.arbitrage.screener.exchange.mexc;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * WebSocket-адаптер для биржи MEXC (фьючерсный рынок).
 * Подключается к wss://contract.mexc.com/edge,
 * подписывается на push.ticker и нормализует в {@link Ticker}.
 */
@Slf4j
public class MexcAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final int PING_INTERVAL_SECONDS = 60;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;

    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<Ticker>> tickerHandlers;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final AtomicInteger reconnectAttempt;

    private volatile ExchangeWebSocketClient webSocketClient;
    private volatile boolean connected;
    private volatile ScheduledFuture<?> pingTask;
    private String[] subscribedSymbols;

    public MexcAdapter() {
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
            return;
        }
        doConnect();
    }

    private void doConnect() {
        var targetUri = URI.create(exchange.getWsUrl());
        this.webSocketClient = new ExchangeWebSocketClient(
                targetUri,
                "MEXC",
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
        } catch (Exception e) {
            log.error("MEXC: failed to connect: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void onConnected() {
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("MEXC: connected to webSocket");
        startPing();
        subscribeAll();
    }

    @Override
    public void disconnect() {
        log.info("MEXC: disconnecting from webSocket");
        connected = false;
        stopPing();
        if (webSocketClient != null && webSocketClient.isOpen()) {
            unsubscribeAll();
            webSocketClient.close();
        }
    }

    @Override
    public void subscribe(String... symbols) {
        if (symbols.length == 0) {
            log.warn("No symbols provided for subscription");
            return;
        }
        this.subscribedSymbols = symbols;
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
        var client = this.webSocketClient;
        if (client == null || !connected) return;

        for (var symbol : subscribedSymbols) {
            var formattedSymbol = formatSymbol(symbol);
            var msg = buildSubscriptionMessage(formattedSymbol);
            log.info("MEXC: subscribing to ticker for {}", formattedSymbol);
            client.send(msg);
        }
    }

    private void unsubscribeAll() {
        if (subscribedSymbols == null || subscribedSymbols.length == 0) return;
        var client = this.webSocketClient;
        if (client == null || !client.isOpen()) return;

        for (var symbol : subscribedSymbols) {
            var formattedSymbol = formatSymbol(symbol);
            var msg = buildUnsubscriptionMessage(formattedSymbol);
            client.send(msg);
        }
    }

    private String buildSubscriptionMessage(String symbol) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("method", "sub.ticker")
                    .set("param", MAPPER.createObjectNode().put("symbol", symbol));
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("MEXC: failed to build subscription message", e);
        }
    }

    private String buildUnsubscriptionMessage(String symbol) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("method", "unsub.ticker")
                    .set("param", MAPPER.createObjectNode().put("symbol", symbol));
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("MEXC: failed to build unsubscription message", e);
        }
    }

    /**
     * Форматирует символ в формат MEXC futures: BTCUSDT → BTC_USDT.
     */
    private String formatSymbol(String symbol) {
        var upper = symbol.toUpperCase();
        if (upper.contains("_")) return upper;
        if (upper.endsWith("USDT")) {
            return upper.substring(0, upper.length() - 4) + "_USDT";
        }
        return upper;
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
            try {
                var pingMsg = MAPPER.writeValueAsString(
                        MAPPER.createObjectNode().put("method", "ping")
                );
                client.send(pingMsg);
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
        log.info("MEXC: reconnect attempt {} in {} seconds", attempt, delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    // --- Message Handling ---

    public void handleMessage(String text) {
        try {
            var root = MAPPER.readTree(text);

            if (root.has("channel") && "pong".equals(root.get("channel").asText())) {
                log.debug("MEXC: pong received");
                return;
            }

            if (root.has("code") && !root.has("channel")) {
                log.debug("MEXC: received ack: code={}", root.get("code").asInt());
                return;
            }

            if ("push.ticker".equals(root.path("channel").asText())) {
                parseTicker(root);
            } else {
                log.debug("MEXC: skipping unrecognized message: {}", text);
            }
        } catch (JsonProcessingException e) {
            log.error("MEXC: failed to parse webSocket message: {}", e.getMessage());
        }
    }

    void parseTicker(JsonNode root) {
        var symbol = root.get("symbol").asText().replace("_", "");
        var data = root.get("data");

        var bidPrice = new BigDecimal(data.get("bid1").asText());
        var askPrice = new BigDecimal(data.get("ask1").asText());

        var ticker = new Ticker(exchange, symbol, bidPrice, BigDecimal.ZERO, askPrice, BigDecimal.ZERO, Instant.now());
        tickerHandlers.forEach(h -> h.accept(ticker));

        var bids = List.of(new OrderBookLevel(bidPrice, BigDecimal.ZERO));
        var asks = List.of(new OrderBookLevel(askPrice, BigDecimal.ZERO));
        var orderBook = new OrderBook(exchange, symbol, bids, asks, Instant.now());
        orderBookHandlers.forEach(h -> h.accept(orderBook));
    }

    public void handleClose(int statusCode, String reason) {
        log.warn("MEXC: webSocket closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (statusCode != org.java_websocket.framing.CloseFrame.NORMAL) {
            scheduleReconnect();
        }
    }
}
