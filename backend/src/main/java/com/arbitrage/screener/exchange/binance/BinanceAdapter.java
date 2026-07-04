package com.arbitrage.screener.exchange.binance;

import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeWebSocketClient;
import com.arbitrage.screener.model.BookTicker;
import com.arbitrage.screener.model.Deal;
import com.arbitrage.screener.model.OrderBook;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ch.qos.logback.core.util.StringUtil.isNullOrEmpty;
import static com.arbitrage.screener.util.FormatHelper.*;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * WebSocket-адаптер для биржи Binance (USDⓈ-M Futures).
 * Использует два независимых подключения:
 * <ul>
 *   <li>{@code wss://fstream.binance.com/public/ws/{symbol}@depth20@500ms} — стакан ордеров (20 уровней, 500 мс)</li>
 *   <li>{@code wss://fstream.binance.com/market/ws/{symbol}@aggTrade} — агрегированные сделки</li>
 * </ul>
 * Нормализует данные в {@link Deal} и {@link OrderBook}.
 */
@Slf4j
public class BinanceAdapter implements ExchangeAdapter {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final String DEPTH_ENDPOINT = "wss://fstream.binance.com/public/ws/";
    private static final String DEAL_ENDPOINT = "wss://fstream.binance.com/market/ws/";
    private static final String BOOK_TICKER_ENDPOINT = "wss://fstream.binance.com/public/ws/"; //btcusdc@bookTicker

    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;

    private final ScheduledExecutorService scheduler;
    private final ExchangeEnum exchange;
    private final List<Consumer<OrderBook>> orderBookHandlers;
    private final List<Consumer<Deal>> dealHandlers;
    private final List<Consumer<BookTicker>> bookTickerHandlers;

    private final AtomicInteger depthReconnectAttempt;
    private final AtomicInteger dealReconnectAttempt;
    private final AtomicInteger bookTickerReconnectAttempt;

    private final AtomicBoolean depthReconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean dealReconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean bookTickerReconnectScheduled = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> depthReconnectFuture;
    private volatile ScheduledFuture<?> dealReconnectFuture;
    private volatile ScheduledFuture<?> bookTickerReconnectFuture;

    private volatile ExchangeWebSocketClient depthWsClient;
    private volatile ExchangeWebSocketClient dealWsClient;
    private volatile ExchangeWebSocketClient bookTickerWsClient;

    private volatile boolean depthConnected;
    private volatile boolean dealConnected;
    private volatile boolean bookTickerConnected;
    private volatile boolean closing;
    private volatile String subscribedSymbol;

    public BinanceAdapter() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "binance-ws");
            thread.setDaemon(true);
            return thread;
        });
        this.exchange = ExchangeEnum.BINANCE;
        this.orderBookHandlers = new CopyOnWriteArrayList<>();
        this.dealHandlers = new CopyOnWriteArrayList<>();
        this.bookTickerHandlers = new CopyOnWriteArrayList<>();
        this.depthReconnectAttempt = new AtomicInteger(0);
        this.dealReconnectAttempt = new AtomicInteger(0);
        this.bookTickerReconnectAttempt = new AtomicInteger(0);
    }

    @Override
    public void connect() {
        if (!isConnected()) {
            doConnect();
        }
    }

    private void doConnect() {
        closing = false;
        connectDepth();
        connectDeal();
        connectBookTicker();
    }

    private void connectDepth() {
        var targetUri = buildDepthUri();
        if (targetUri == null) return;

        this.depthWsClient = new ExchangeWebSocketClient(
                targetUri,
                "Binance-Depth",
                this::onDepthConnected,
                this::handleDepthMessage,
                this::handleDepthClose,
                () -> {
                    depthConnected = false;
                    scheduleDepthReconnect();
                }
        );
        try {
            this.depthWsClient.connect();
            this.depthConnected = true;
        } catch (Exception e) {
            log.error("Binance-Depth: failed to connect: {}", e.getMessage());
            scheduleDepthReconnect();
        }
    }

    private void connectDeal() {
        var targetUri = buildDealUri();
        if (targetUri == null) return;

        this.dealWsClient = new ExchangeWebSocketClient(
                targetUri,
                "Binance-Deal",
                this::onDealConnected,
                this::handleDealMessage,
                this::handleDealClose,
                () -> {
                    dealConnected = false;
                    scheduleDealReconnect();
                }
        );
        try {
            this.dealWsClient.connect();
            this.dealConnected = true;
        } catch (Exception e) {
            log.error("Binance-Deal: failed to connect: {}", e.getMessage());
            scheduleDealReconnect();
        }
    }

    private void connectBookTicker() {
        var targetUri = buildBookTickerUri();
        if (targetUri == null) return;

        this.bookTickerWsClient = new ExchangeWebSocketClient(
                targetUri,
                "Binance-BookTicker",
                this::onBookTickerConnected,
                this::handleBookTickerMessage,
                this::handleBookTickerClose,
                () -> {
                    bookTickerConnected = false;
                    scheduleBookTickerReconnect();
                }
        );
        try {
            this.bookTickerWsClient.connect();
            this.bookTickerConnected = true;
        } catch (Exception e) {
            log.error("Binance-BookTicker: failed to connect: {}", e.getMessage());
            scheduleBookTickerReconnect();
        }
    }

    private URI buildDepthUri() {
        if (isNullOrEmpty(subscribedSymbol)) return null;
        return URI.create(DEPTH_ENDPOINT + subscribedSymbol.toLowerCase() + "@depth20@500ms");
    }

    private URI buildDealUri() {
        if (isNullOrEmpty(subscribedSymbol)) return null;
        return URI.create(DEAL_ENDPOINT + subscribedSymbol.toLowerCase() + "@aggTrade");
    }

    private URI buildBookTickerUri() {
        if (isNullOrEmpty(subscribedSymbol)) return null;
        return URI.create(BOOK_TICKER_ENDPOINT + subscribedSymbol.toLowerCase() + "@bookTicker");
    }

    private void onDepthConnected() {
        this.depthConnected = true;
        this.depthReconnectAttempt.set(0);
        log.info("Binance-Depth: connected to ws");
    }

    private void onDealConnected() {
        this.dealConnected = true;
        this.dealReconnectAttempt.set(0);
        log.info("Binance-Deal: connected to ws");
    }

    private void onBookTickerConnected() {
        this.bookTickerConnected = true;
        this.bookTickerReconnectAttempt.set(0);
        log.info("Binance-BookTicker: connected to ws");
    }

    @Override
    public void disconnect() {
        log.info("Binance: disconnecting from ws");
        closing = true;
        cancelReconnect(depthReconnectFuture, depthReconnectScheduled);
        cancelReconnect(dealReconnectFuture, dealReconnectScheduled);
        cancelReconnect(bookTickerReconnectFuture, bookTickerReconnectScheduled);
        depthConnected = false;
        dealConnected = false;
        bookTickerConnected = false;
        disconnectDepth();
        disconnectDeal();
        disconnectBookTicker();
    }

    private void cancelReconnect(ScheduledFuture<?> future, AtomicBoolean scheduled) {
        scheduled.set(false);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void disconnectDepth() {
        if (depthWsClient != null && depthWsClient.isOpen()) {
            depthWsClient.close();
        }
    }

    private void disconnectDeal() {
        if (dealWsClient != null && dealWsClient.isOpen()) {
            dealWsClient.close();
        }
    }

    private void disconnectBookTicker() {
        if (bookTickerWsClient != null && bookTickerWsClient.isOpen()) {
            bookTickerWsClient.close();
        }
    }

    @Override
    public void shutdown() {
        log.info("Binance: shutting down adapter");
        disconnect();
        scheduler.shutdownNow();
    }

    @Override
    public void subscribe(String symbol) {
        if (isNullOrEmpty(symbol)) {
            log.warn("Binance: no symbols provided for subscription");
            return;
        }
        this.subscribedSymbol = symbol;
        if (depthConnected || dealConnected || bookTickerConnected) {
            disconnect();
            closing = false;
            doConnect();
        }
    }

    @Override
    public boolean isConnected() {
        return depthConnected && dealConnected && bookTickerConnected;
    }

    @Override
    public ExchangeEnum getExchange() {
        return exchange;
    }

    @Override
    public void onOrderBook(Consumer<OrderBook> handler) {
        orderBookHandlers.add(handler);
    }

    public void onBookTicker(Consumer<BookTicker> handler) {
        bookTickerHandlers.add(handler);
    }

    @Override
    public void onDeal(Consumer<Deal> handler) {
        dealHandlers.add(handler);
    }

    @Override
    public void clearHandlers() {
        orderBookHandlers.clear();
        dealHandlers.clear();
        bookTickerHandlers.clear();
    }

    // --- Reconnection ---

    private void scheduleDepthReconnect() {
        if (closing || !depthReconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        depthConnected = false;
        disconnectDepth();
        var attempt = depthReconnectAttempt.incrementAndGet();
        var delay = reconnectDelay(attempt);
        log.info("Binance-Depth: reconnect attempt {} in {} seconds", attempt, delay);
        depthReconnectFuture = scheduler.schedule(this::reconnectDepth, delay, TimeUnit.SECONDS);
    }

    private void reconnectDepth() {
        depthReconnectScheduled.set(false);
        if (!closing) {
            connectDepth();
        }
    }

    private void scheduleDealReconnect() {
        if (closing || !dealReconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        dealConnected = false;
        disconnectDeal();
        var attempt = dealReconnectAttempt.incrementAndGet();
        var delay = reconnectDelay(attempt);
        log.info("Binance-Deal: reconnect attempt {} in {} seconds", attempt, delay);
        dealReconnectFuture = scheduler.schedule(this::reconnectDeal, delay, TimeUnit.SECONDS);
    }

    private void reconnectDeal() {
        dealReconnectScheduled.set(false);
        if (!closing) {
            connectDeal();
        }
    }

    private void scheduleBookTickerReconnect() {
        if (closing || !bookTickerReconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        bookTickerConnected = false;
        disconnectBookTicker();
        var attempt = bookTickerReconnectAttempt.incrementAndGet();
        var delay = reconnectDelay(attempt);
        log.info("Binance-BookTicker: reconnect attempt {} in {} seconds", attempt, delay);
        bookTickerReconnectFuture = scheduler.schedule(this::reconnectBookTicker, delay, TimeUnit.SECONDS);
    }

    private void reconnectBookTicker() {
        bookTickerReconnectScheduled.set(false);
        if (!closing) {
            connectBookTicker();
        }
    }

    private long reconnectDelay(int attempt) {
        return Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (long) Math.pow(2, attempt - 1),
                MAX_RECONNECT_DELAY_SECONDS
        );
    }

    // --- Message Handling ---

    private void handleDepthMessage(String text) {
        try {
            var root = MAPPER.readTree(text);
            var eventType = root.path("e").asText();

            if ("depthUpdate".equals(eventType)) {
                parseDepth(root);
            } else {
                log.debug("Binance-Depth: unhandled event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Binance-Depth: failed to parse message: {}", e.getMessage());
        }
    }

    private void handleDealMessage(String text) {
        try {
            var root = MAPPER.readTree(text);
            var eventType = root.path("e").asText();

            if ("aggTrade".equals(eventType)) {
                parseDeal(root);
            } else {
                log.debug("Binance-Deal: unhandled event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Binance-Deal: failed to parse message: {}", e.getMessage());
        }
    }

    private void handleBookTickerMessage(String text) {
        try {
            var root = MAPPER.readTree(text);
            var eventType = root.path("e").asText();

            if ("bookTicker".equals(eventType)) {
                parseBookTicker(root);
            } else {
                log.debug("Binance-BookTicker: unhandled event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Binance-BookTicker: failed to parse message: {}", e.getMessage());
        }
    }

    private void parseDepth(JsonNode data) {
        var symbol = normalizeSymbol(data.get("s").asText());
        if (!data.has("b") || !data.has("a")) {
            log.debug("Binance-Depth: message without bids/asks for symbol={}, skipping", symbol);
            return;
        }
        var timestamp = parseEventTime(data);
        buildAndBroadcastOrderBook(exchange, symbol, data.get("b"), data.get("a"), timestamp, orderBookHandlers);
    }

    private void parseDeal(JsonNode data) {
        var symbol = normalizeSymbol(data.get("s").asText());
        var price = new BigDecimal(data.get("p").asText());
        var qty = new BigDecimal(data.get("q").asText());
        var timestamp = parseEventTime(data);

        var deal = new Deal(exchange, symbol, price, qty, timestamp);
        dealHandlers.forEach(h -> h.accept(deal));
    }

    private void parseBookTicker(JsonNode data) {
        var symbol = normalizeSymbol(data.get("s").asText());
        var askPrice = new BigDecimal(data.get("a").asText());
        var askQty = new BigDecimal(data.get("A").asText());
        var bidPrice = new BigDecimal(data.get("b").asText());
        var bidQty = new BigDecimal(data.get("B").asText());
        var timestamp = Long.valueOf(data.get("T").asLong());

        var bookTicker = new BookTicker(exchange, symbol, askPrice, askQty, bidPrice, bidQty, timestamp);
        bookTickerHandlers.forEach(h -> h.accept(bookTicker));
    }

    private void handleDepthClose(int statusCode, String reason) {
        log.warn("Binance-Depth: ws closed: code={}, reason={}", statusCode, reason);
        depthConnected = false;
        if (!closing) {
            scheduleDepthReconnect();
        }
    }

    private void handleDealClose(int statusCode, String reason) {
        log.warn("Binance-Deal: ws closed: code={}, reason={}", statusCode, reason);
        dealConnected = false;
        if (!closing) {
            scheduleDealReconnect();
        }
    }

    private void handleBookTickerClose(int statusCode, String reason) {
        log.warn("Binance-BookTicker: ws closed: code={}, reason={}", statusCode, reason);
        bookTickerConnected = false;
        if (!closing) {
            scheduleBookTickerReconnect();
        }
    }
}
