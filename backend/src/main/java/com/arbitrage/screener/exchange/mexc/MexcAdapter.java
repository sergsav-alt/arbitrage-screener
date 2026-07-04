package com.arbitrage.screener.exchange.mexc;

import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeWebSocketClient;
import com.arbitrage.screener.model.BookTicker;
import com.arbitrage.screener.model.Deal;
import com.arbitrage.screener.model.OrderBook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ch.qos.logback.core.util.StringUtil.isNullOrEmpty;
import static com.arbitrage.screener.util.FormatHelper.buildAndBroadcastOrderBook;
import static com.arbitrage.screener.util.FormatHelper.formatSymbol;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * WebSocket-адаптер для биржи MEXC (фьючерсный рынок).
 * Подключается к wss://contract.mexc.com/edge,
 * подписывается на push.deal (сделки) и push.depth (стакан ордеров),
 * нормализует в {@link Deal} и {@link OrderBook}.
 * Клиент отправляет ping каждые 60 секунд ({@code {"method":"ping"}}).
 */
@Slf4j
public class MexcAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final int PING_INTERVAL_SECONDS = 60;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;

    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<Deal>> dealHandlers;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final List<Consumer<BookTicker>> bookTickerHandlers;
    private final AtomicInteger reconnectAttempt;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private volatile ExchangeWebSocketClient webSocketClient;
    private volatile boolean connected = false;
    private volatile boolean closing = false;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile ScheduledFuture<?> pingTask;
    private volatile String subscribedSymbol;

    public MexcAdapter() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "mexc-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.MEXC;
        this.dealHandlers = new CopyOnWriteArrayList<>();
        this.orderBookHandlers = new CopyOnWriteArrayList<>();
        this.bookTickerHandlers = new CopyOnWriteArrayList<>();
        this.reconnectAttempt = new AtomicInteger(0);
    }

    @Override
    public void connect() {
        if (!connected) {
            doConnect();
        }
    }

    private void doConnect() {
        closing = false;
        var targetUri = URI.create(exchange.getWsUrl());
        this.webSocketClient = new ExchangeWebSocketClient(
                targetUri,
                "MEXC",
                this::onConnected,
                this::handleMessage,
                this::handleClose,
                this::onError
        );
        try {
            this.webSocketClient.connect();
            this.connected = true;
        } catch (Exception e) {
            log.error("MEXC: failed to connect: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void onError() {
        connected = false;
        stopPing();
        scheduleReconnect();
    }

    private void onConnected() {
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("MEXC: connected to ws");
        startPing();
        subscribe();
    }

    @Override
    public void disconnect() {
        log.info("MEXC: disconnecting from ws");
        closing = true;
        cancelReconnect();
        connected = false;
        stopPing();
        if (webSocketClient != null && webSocketClient.isOpen()) {
            unsubscribe();
            webSocketClient.close();
        }
    }

    @Override
    public void shutdown() {
        log.info("MEXC: shutting down adapter");
        disconnect();
        scheduler.shutdownNow();
    }

    @Override
    public void subscribe(String symbol) {
        if (isNullOrEmpty(symbol)) {
            log.warn("No symbols provided for subscription");
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
    public void onDeal(Consumer<Deal> handler) {
        dealHandlers.add(handler);
    }

    @Override
    public void onBookTicker(Consumer<BookTicker> handler) {
        bookTickerHandlers.add(handler);
    }

    @Override
    public void onOrderBook(Consumer<OrderBook> handler) {
        orderBookHandlers.add(handler);
    }

    @Override
    public void clearHandlers() {
        dealHandlers.clear();
        orderBookHandlers.clear();
        bookTickerHandlers.clear();
    }

    // --- Subscription ---

    private void subscribe() {
        var client = this.webSocketClient;
        if (client == null || !connected) return;

        var formattedSymbol = formatSymbol(subscribedSymbol, exchange);

        var dealMsg = buildSubscriptionMessage("sub.deal", formattedSymbol);
        client.send(dealMsg);

        var depthMsg = buildSubscriptionMessage("sub.depth", formattedSymbol);
        client.send(depthMsg);

        var tickerMsg = buildSubscriptionMessage("sub.ticker", formattedSymbol);
        client.send(tickerMsg);
    }

    private void unsubscribe() {
        var client = this.webSocketClient;
        if (client == null || !client.isOpen()) return;

        var formattedSymbol = formatSymbol(subscribedSymbol, exchange);

        var dealMsg = buildUnsubscriptionMessage("unsub.deal", formattedSymbol);
        client.send(dealMsg);

        var depthMsg = buildUnsubscriptionMessage("unsub.depth", formattedSymbol);
        client.send(depthMsg);

        var tickerMsg = buildUnsubscriptionMessage("unsub.ticker", formattedSymbol);
        client.send(tickerMsg);
    }

    private String buildSubscriptionMessage(String method, String symbol) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("method", method)
                    .set("param", MAPPER.createObjectNode().put("symbol", symbol));
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("MEXC: failed to build subscription message", e);
        }
    }

    private String buildUnsubscriptionMessage(String method, String symbol) {
        try {
            var node = MAPPER.createObjectNode()
                    .put("method", method)
                    .set("param", MAPPER.createObjectNode().put("symbol", symbol));
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("MEXC: failed to build unsubscription message", e);
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
        if (closing) {
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        connected = false;
        stopPing();
        closeClient();
        var attempt = reconnectAttempt.incrementAndGet();
        var delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1),
                MAX_RECONNECT_DELAY_SECONDS
        );
        log.info("MEXC: reconnect attempt {} in {} seconds", attempt, delay);
        reconnectFuture = scheduler.schedule(this::reconnect, delay, TimeUnit.SECONDS);
    }

    private void reconnect() {
        reconnectScheduled.set(false);
        if (closing) {
            return;
        }
        doConnect();
    }

    private void cancelReconnect() {
        reconnectScheduled.set(false);
        var future = reconnectFuture;
        if (future != null) {
            future.cancel(false);
            reconnectFuture = null;
        }
    }

    private void closeClient() {
        var client = this.webSocketClient;
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    // --- Message Handling ---

    private void handleMessage(String text) {
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

            var channel = root.path("channel").asText();
            if ("push.deal".equals(channel)) {
                parseDeal(root);
            } else if ("push.depth".equals(channel)) {
                parseDepth(root);
            } else if ("push.ticker".equals(channel)) {
                parseBookTicker(root);
            } else {
                log.debug("MEXC: skipping unrecognized message: {}", text);
            }
        } catch (JsonProcessingException e) {
            log.error("MEXC: failed to parse ws message: {}", e.getMessage());
        }
    }

    private void parseDeal(JsonNode root) {
        var symbol = formSymbol(root);
        var timestamp = root.get("ts").asLong();
        var data = root.get("data");

        var price = new BigDecimal(data.get(0).get("p").asText());
        var quantity = new BigDecimal(data.get(0).get("v").asText());

        var deal = new Deal(exchange, symbol, price, quantity, timestamp);
        dealHandlers.forEach(h -> h.accept(deal));
    }

    private void parseBookTicker(JsonNode root) {
        var data = root.get("data");

        var askPrice = new BigDecimal(data.get("minAskPrice").asText());
        var bidPrice = new BigDecimal(data.get("maxBidPrice").asText());
        var timestamp = Long.valueOf(data.get("timestamp").asLong());

        var bookTicker = new BookTicker(exchange, formSymbol(root), askPrice, BigDecimal.valueOf(0), bidPrice, BigDecimal.valueOf(0), timestamp);
        bookTickerHandlers.forEach(h -> h.accept(bookTicker));
    }

    private void parseDepth(JsonNode root) {
        var symbol = formSymbol(root);
        var data = root.get("data");
        if (data == null || !data.has("bids") || !data.has("asks")) {
            log.debug("MEXC: depth message without bids/asks for symbol={}, skipping", symbol);
            return;
        }
        var timestamp = root.has("ts") ? root.get("ts").asLong() : 0L;
        buildAndBroadcastOrderBook(exchange, formSymbol(root), data.get("bids"), data.get("asks"), timestamp, orderBookHandlers);
    }

    private String formSymbol(JsonNode root) {
        return root.get("symbol").asText().replace("_", "");
    }

    private void handleClose(int statusCode, String reason) {
        log.warn("MEXC: ws closed: code={}, reason={}", statusCode, reason);
        connected = false;
        stopPing();
        if (!closing && statusCode != org.java_websocket.framing.CloseFrame.NORMAL) {
            scheduleReconnect();
        }
    }
}
