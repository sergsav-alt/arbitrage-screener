package com.arbitrage.screener.exchange.bingx;

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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ch.qos.logback.core.util.StringUtil.isNullOrEmpty;
import static com.arbitrage.screener.util.FormatHelper.*;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;
import static com.arbitrage.screener.util.ObjectMapperHelper.writeValueAsString;

/**
 * WebSocket-адаптер для биржи BingX (деривативы, swap).
 * Подключается к wss://open-api-swap.bingx.com/swap-market,
 * подписывается на Deal (сделки), depth50@200ms (стакан ордеров) и bookTicker (лучший бид/аск),
 * нормализует в {@link Deal}, {@link OrderBook} и {@link BookTicker}.
 * Сервер шлет Ping каждые 5 секунд (gzip), клиент отвечает Pong (обрабатывается в {@link ExchangeWebSocketClient}).
 */
@Slf4j
public class BingXAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

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
    private volatile String subscribedSymbol;

    public BingXAdapter() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "bingx-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.BINGX;
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
                "BingX",
                this::onConnected,
                this::handleMessage,
                this::handleClose,
                this::onError
        );
        try {
            this.webSocketClient.connect();
            this.connected = true;
        } catch (Exception e) {
            log.error("BingX: failed to connect to ws: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void onError() {
        connected = false;
        scheduleReconnect();
    }

    private void onConnected() {
        this.connected = true;
        this.reconnectAttempt.set(0);
        log.info("BingX: connected to ws");
        subscribe();
    }

    @Override
    public void disconnect() {
        log.info("BingX: disconnecting from ws");
        closing = true;
        cancelReconnect();
        connected = false;
        if (webSocketClient != null && webSocketClient.isOpen()) {
            unsubscribe();
            webSocketClient.close();
        }
    }

    @Override
    public void shutdown() {
        log.info("BingX: shutting down adapter");
        disconnect();
        scheduler.shutdownNow();
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

        var dealMsg = buildSubscriptionMessage(formattedSymbol, "@trade");
        client.send(dealMsg);

        var depthMsg = buildSubscriptionMessage(formattedSymbol, "@depth50@200ms");
        client.send(depthMsg);

        var bookTickerMsg = buildSubscriptionMessage(formattedSymbol, "@bookTicker");
        client.send(bookTickerMsg);
    }

    private void unsubscribe() {
        var client = this.webSocketClient;
        if (client == null || !client.isOpen()) return;

        var formattedSymbol = formatSymbol(subscribedSymbol, exchange);

        var dealMsg = buildUnsubscriptionMessage(formattedSymbol, "@trade");
        client.send(dealMsg);

        var depthMsg = buildUnsubscriptionMessage(formattedSymbol, "@depth50@200ms");
        client.send(depthMsg);

        var bookTickerMsg = buildUnsubscriptionMessage(formattedSymbol, "@bookTicker");
        client.send(bookTickerMsg);
    }

    private String buildSubscriptionMessage(String symbol, String dataType) {
        var node = MAPPER.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("reqType", "sub")
                .put("dataType", symbol + dataType);
        return writeValueAsString(node);
    }

    private String buildUnsubscriptionMessage(String symbol, String dataType) {
        var node = MAPPER.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("reqType", "unsub")
                .put("dataType", symbol + dataType);
        return writeValueAsString(node);
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
        closeClient();
        var attempt = reconnectAttempt.incrementAndGet();
        var delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1),
                MAX_RECONNECT_DELAY_SECONDS
        );
        log.info("BingX: reconnect attempt {} in {} seconds", attempt, delay);
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

            if (root.has("code") && !root.has("dataType")) {
                log.debug("BingX: received subscription confirmation: code={}", root.get("code").asInt());
                return;
            }

            var dataTypeNode = root.get("dataType");
            if (dataTypeNode == null) {
                log.debug("BingX: skipping message without dataType");
                return;
            }

            var dataType = dataTypeNode.asText();

            var dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                return;
            }

            if (dataType.contains("trade")) {
                parseDeal(dataNode);
            } else if (dataType.contains("bookTicker")) {
                var symbol = normalizeSymbol(dataType.substring(0, dataType.indexOf('@')));
                parseBookTicker(symbol, dataNode);
            } else if (dataType.contains("depth")) {
                var symbol = normalizeSymbol(dataType.substring(0, dataType.indexOf('@')));
                parseDepth(symbol, dataNode);
            }
        } catch (JsonProcessingException e) {
            log.error("BingX: failed to parse ws message: {}", e.getMessage());
        }
    }

    private void parseDeal(JsonNode node) {
        var data = node.get(0);
        var symbol = data.get("s").asText();
        var price = new BigDecimal(data.get("p").asText());
        var quantity = new BigDecimal(data.get("q").asText());

        var deal = new Deal(exchange, normalizeSymbol(symbol), price, quantity, parseEventTime(data));
        dealHandlers.forEach(h -> h.accept(deal));
    }

    private void parseBookTicker(String symbol, JsonNode data) {
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var timestamp = data.get("E").asLong();

        var bookTicker = new BookTicker(exchange, symbol, askPrice, askQty, bidPrice, bidQty, timestamp);
        bookTickerHandlers.forEach(h -> h.accept(bookTicker));
    }

    private void parseDepth(String symbol, JsonNode data) {
        if (!data.has("bids") || !data.has("asks")) {
            log.debug("Binance: depth message without bids/asks for symbol={}, skipping", symbol);
            return;
        }
        buildAndBroadcastOrderBook(exchange, symbol, data.get("bids"), data.get("asks"), parseTimestamp(data), orderBookHandlers);
    }

    private void handleClose(int statusCode, String reason) {
        log.warn("BingX: ws closed: code={}, reason={}", statusCode, reason);
        connected = false;
        if (!closing && statusCode != org.java_websocket.framing.CloseFrame.NORMAL) {
            scheduleReconnect();
        }
    }
}
