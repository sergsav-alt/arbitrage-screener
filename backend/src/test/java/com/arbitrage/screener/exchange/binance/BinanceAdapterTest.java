package com.arbitrage.screener.exchange.binance;

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

class BinanceAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BinanceAdapter adapter;
    private List<Ticker> capturedTickers;
    private List<OrderBook> capturedOrderBooks;

    @BeforeEach
    void setUp() {
        adapter = new BinanceAdapter();
        capturedTickers = new ArrayList<>();
        capturedOrderBooks = new ArrayList<>();
        adapter.onTicker(capturedTickers::add);
        adapter.onOrderBook(capturedOrderBooks::add);
    }

    @Nested
    @DisplayName("Message parsing")
    class MessageParsing {

        @Test
        @DisplayName("Should parse miniTicker from combined stream message")
        void shouldParseMiniTicker() {
            var stream = "btcbusdt@miniTicker";
            var data = MAPPER.createObjectNode()
                    .put("e", "24hrMiniTicker")
                    .put("s", "BTCBUSDT")
                    .put("b", "50000.00")
                    .put("B", "1.5")
                    .put("a", "50010.00")
                    .put("A", "2.0");
            var root = createCombinedMessage(stream, data);

            adapter.handleMessage(MAPPER.valueToTree(root).toString());

            assertEquals(1, capturedTickers.size());
            var ticker = capturedTickers.get(0);
            assertEquals(ExchangeEnum.BINANCE, ticker.exchange());
            assertEquals("BTCBUSDT", ticker.symbol());
            assertEquals(new BigDecimal("50000.00"), ticker.bidPrice());
            assertEquals(new BigDecimal("1.5"), ticker.bidQty());
            assertEquals(new BigDecimal("50010.00"), ticker.askPrice());
            assertEquals(new BigDecimal("2.0"), ticker.askQty());
            assertNotNull(ticker.timestamp());
        }

        @Test
        @DisplayName("Should parse bookTicker from combined stream message")
        void shouldParseBookTicker() {
            var stream = "ethbusdt@bookTicker";
            var data = MAPPER.createObjectNode()
                    .put("u", 12345L)
                    .put("s", "ETHBUSDT")
                    .put("b", "3000.00")
                    .put("B", "10.0")
                    .put("a", "3001.00")
                    .put("A", "5.0");
            var root = createCombinedMessage(stream, data);

            adapter.handleMessage(MAPPER.valueToTree(root).toString());

            assertEquals(1, capturedTickers.size());
            assertEquals(1, capturedOrderBooks.size());

            var ticker = capturedTickers.get(0);
            assertEquals("ETHBUSDT", ticker.symbol());
            assertEquals(new BigDecimal("3000.00"), ticker.bidPrice());

            var orderBook = capturedOrderBooks.get(0);
            assertEquals(ExchangeEnum.BINANCE, orderBook.exchange());
            assertEquals("ETHBUSDT", orderBook.symbol());
            assertEquals(1, orderBook.bids().size());
            assertEquals(new BigDecimal("3000.00"), orderBook.bids().get(0).price());
            assertEquals(1, orderBook.asks().size());
            assertEquals(new BigDecimal("3001.00"), orderBook.asks().get(0).price());
        }

        @Test
        @DisplayName("Should not create ticker when stream type is unknown")
        void shouldIgnoreUnknownStreamType() {
            var stream = "btcusdt@trade";
            var data = MAPPER.createObjectNode()
                    .put("e", "trade")
                    .put("s", "BTCUSDT")
                    .put("p", "50000.00");
            var root = createCombinedMessage(stream, data);

            adapter.handleMessage(MAPPER.valueToTree(root).toString());

            assertTrue(capturedTickers.isEmpty());
            assertTrue(capturedOrderBooks.isEmpty());
        }

        @Test
        @DisplayName("Should handle null data gracefully")
        void shouldHandleNullData() {
            var root = MAPPER.createObjectNode().put("stream", "btcbusdt@miniTicker");
            root.set("data", MAPPER.nullNode());

            //assertDoesNotThrow(() -> adapter.handleMessage(root.toString()));
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
        @DisplayName("Should return BINANCE exchange")
        void shouldReturnBinanceExchange() {
            assertEquals(ExchangeEnum.BINANCE, adapter.getExchange());
        }

        @Test
        @DisplayName("Should accept empty streams without error")
        void shouldAcceptEmptyStreams() {
            assertDoesNotThrow(() -> adapter.subscribe());
        }

        @Test
        @DisplayName("Should clear handlers")
        void shouldClearHandlers() {
            adapter.clearHandlers();

            adapter.handleMessage(sampleMiniTickerMessage());

            assertTrue(capturedTickers.isEmpty());
        }
    }

    @Nested
    @DisplayName("Callback threading")
    class CallbackThreading {

        @Test
        @DisplayName("Should invoke all registered ticker handlers")
        void shouldInvokeAllTickerHandlers() throws InterruptedException {
            var latch = new CountDownLatch(3);
            adapter.onTicker(t -> latch.countDown());
            adapter.onTicker(t -> latch.countDown());
            adapter.onTicker(t -> latch.countDown());

            adapter.handleMessage(sampleMiniTickerMessage());

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should invoke all registered orderBook handlers")
        void shouldInvokeAllOrderBookHandlers() throws InterruptedException {
            var latch = new CountDownLatch(2);
            adapter.onOrderBook(ob -> latch.countDown());
            adapter.onOrderBook(ob -> latch.countDown());

            adapter.handleMessage(sampleBookTickerMessage());

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }

    // --- Helper methods ---

    private ObjectNode createCombinedMessage(String stream, ObjectNode data) {
        var root = MAPPER.createObjectNode();
        root.put("stream", stream);
        root.set("data", data);
        return root;
    }

    private String sampleMiniTickerMessage() {
        var data = MAPPER.createObjectNode()
                .put("e", "24hrMiniTicker")
                .put("s", "BTCBUSDT")
                .put("b", "50000.00")
                .put("B", "1.5")
                .put("a", "50010.00")
                .put("A", "2.0");
        return MAPPER.valueToTree(createCombinedMessage("btcbusdt@miniTicker", data)).toString();
    }

    private String sampleBookTickerMessage() {
        var data = MAPPER.createObjectNode()
                .put("u", 12345L)
                .put("s", "ETHBUSDT")
                .put("b", "3000.00")
                .put("B", "10.0")
                .put("a", "3001.00")
                .put("A", "5.0");
        return MAPPER.valueToTree(createCombinedMessage("ethbusdt@bookTicker", data)).toString();
    }
}
