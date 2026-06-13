package com.arbitrage.screener.exchange.bingx;

import com.arbitrage.screener.entity.OrderBook;
import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BingXAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BingXAdapter adapter;
    private List<Ticker> capturedTickers;
    private List<OrderBook> capturedOrderBooks;

    @BeforeEach
    void setUp() {
        adapter = new BingXAdapter();
        capturedTickers = new ArrayList<>();
        capturedOrderBooks = new ArrayList<>();
        adapter.onTicker(capturedTickers::add);
        adapter.onOrderBook(capturedOrderBooks::add);
    }

    @Nested
    @DisplayName("Message parsing")
    class MessageParsing {

        @Test
        @DisplayName("Should parse bookTicker message")
        void shouldParseBookTicker() {
            adapter.handleMessage(sampleBookTickerMessage("BTC-USDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertEquals(1, capturedTickers.size());
            assertEquals(1, capturedOrderBooks.size());

            var ticker = capturedTickers.get(0);
            assertEquals(ExchangeEnum.BINGX, ticker.exchange());
            assertEquals("BTCUSDT", ticker.symbol());
            assertEquals(new BigDecimal("50000.00"), ticker.bidPrice());
            assertEquals(new BigDecimal("1.5"), ticker.bidQty());
            assertEquals(new BigDecimal("50010.00"), ticker.askPrice());
            assertEquals(new BigDecimal("2.0"), ticker.askQty());
            assertNotNull(ticker.timestamp());
        }

        @Test
        @DisplayName("Should normalize symbol from BTC-USDT to BTCUSDT")
        void shouldNormalizeSymbol() {
            adapter.handleMessage(sampleBookTickerMessage("ETH-USDT", "3000.00", "10.0", "3001.00", "5.0"));

            var ticker = capturedTickers.get(0);
            assertEquals("ETHUSDT", ticker.symbol());
        }

        @Test
        @DisplayName("Should ignore subscription confirmation messages")
        void shouldIgnoreSubscriptionAck() {
            var ack = MAPPER.createObjectNode()
                    .put("id", "test-id")
                    .put("code", 0)
                    .put("msg", "");

            adapter.handleMessage(ack.toString());

            assertTrue(capturedTickers.isEmpty());
            assertTrue(capturedOrderBooks.isEmpty());
        }

        @Test
        @DisplayName("Should skip messages without dataType")
        void shouldSkipMessageWithoutDataType() {
            var msg = MAPPER.createObjectNode()
                    .put("ping", "pong");

            adapter.handleMessage(msg.toString());

            assertTrue(capturedTickers.isEmpty());
        }

        @Test
        @DisplayName("Should skip non-bookTicker data types")
        void shouldSkipNonBookTicker() {
            var msg = MAPPER.createObjectNode()
                    .put("dataType", "BTC-USDT@trade")
                    .set("data", MAPPER.createObjectNode()
                            .put("s", "BTC-USDT")
                            .put("p", "50000.00"));

            adapter.handleMessage(msg.toString());

            assertTrue(capturedTickers.isEmpty());
        }
    }

    @Nested
    @DisplayName("Connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("Should not be connected initially")
        void shouldNotBeConnectedInitially() {
            assertFalse(adapter.isConnected());
        }

        @Test
        @DisplayName("Should return BINGX exchange")
        void shouldReturnBingXExchange() {
            assertEquals(ExchangeEnum.BINGX, adapter.getExchange());
        }

        @Test
        @DisplayName("Should clear handlers")
        void shouldClearHandlers() {
            adapter.clearHandlers();
            adapter.handleMessage(sampleBookTickerMessage("BTC-USDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(capturedTickers.isEmpty());
        }
    }

    @Nested
    @DisplayName("Callback threading")
    class CallbackThreading {

        @Test
        @DisplayName("Should invoke all registered ticker handlers")
        void shouldInvokeAllTickerHandlers() throws InterruptedException {
            var latch = new CountDownLatch(2);
            adapter.onTicker(t -> latch.countDown());
            adapter.onTicker(t -> latch.countDown());

            adapter.handleMessage(sampleBookTickerMessage("BTC-USDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should invoke all registered orderBook handlers")
        void shouldInvokeAllOrderBookHandlers() throws InterruptedException {
            var latch = new CountDownLatch(2);
            adapter.onOrderBook(ob -> latch.countDown());
            adapter.onOrderBook(ob -> latch.countDown());

            adapter.handleMessage(sampleBookTickerMessage("BTC-USDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Symbol formatting")
    class SymbolFormatting {

        @Test
        @DisplayName("Should format BTCUSDT to BTC-USDT for subscription")
        void shouldFormatSymbolForSubscription() {
            adapter.subscribe("BTCUSDT", "ETHUSDT");
            adapter.onTicker(capturedTickers::add);

            adapter.handleMessage(sampleBookTickerMessage("BTC-USDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertEquals(2, capturedTickers.size());
            assertEquals("BTCUSDT", capturedTickers.get(0).symbol());
        }
    }

    // --- Helpers ---

    private String sampleBookTickerMessage(String symbol, String bidPrice, String bidQty, String askPrice, String askQty) {
        var data = MAPPER.createObjectNode()
                .put("e", "bookTicker")
                .put("s", symbol)
                .put("b", bidPrice)
                .put("B", bidQty)
                .put("a", askPrice)
                .put("A", askQty);

        var root = MAPPER.createObjectNode()
                .put("dataType", symbol + "@bookTicker")
                .set("data", data);

        return root.toString();
    }
}
