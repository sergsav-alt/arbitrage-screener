package com.arbitrage.screener.exchange.binance;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;
import static org.junit.jupiter.api.Assertions.*;

class BinanceAdapterTest {

    private static final ObjectMapper MAPPER = objectMapper();

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
        @DisplayName("Should parse bookTicker message")
        void shouldParseBookTicker() {
            adapter.handleMessage(sampleBookTickerMessage("btcusdt@bookTicker", "BTCUSDT",
                    "50000.00", "1.5", "50010.00", "2.0"));

            assertEquals(1, capturedTickers.size());
            assertEquals(1, capturedOrderBooks.size());

            var ticker = capturedTickers.get(0);
            assertEquals(ExchangeEnum.BINANCE, ticker.exchange());
            assertEquals("BTCUSDT", ticker.symbol());
            assertEquals(new BigDecimal("50000.00"), ticker.bidPrice());
            assertEquals(new BigDecimal("1.5"), ticker.bidQty());
            assertEquals(new BigDecimal("50010.00"), ticker.askPrice());
            assertEquals(new BigDecimal("2.0"), ticker.askQty());
            assertNotNull(ticker.timestamp());
        }

        @Test
        @DisplayName("Should parse bookTicker for ETHUSDT")
        void shouldParseBookTickerEth() {
            adapter.handleMessage(sampleBookTickerMessage("ethusdt@bookTicker", "ETHUSDT",
                    "3000.00", "10.0", "3001.00", "5.0"));

            var ticker = capturedTickers.get(0);
            assertEquals("ETHUSDT", ticker.symbol());
        }

        @Test
        @DisplayName("Should parse bookTicker for BNBUSDT")
        void shouldParseBookTickerBnb() {
            adapter.handleMessage(sampleBookTickerMessage("bnbusdt@bookTicker", "BNBUSDT",
                    "300.00", "50.0", "300.50", "30.0"));

            var ticker = capturedTickers.get(0);
            assertEquals("BNBUSDT", ticker.symbol());
        }

        @Test
        @DisplayName("Should handle messages with large quantities")
        void shouldHandleLargeQuantities() {
            adapter.handleMessage(sampleBookTickerMessage("btcusdt@bookTicker", "BTCUSDT",
                    "50000.00", "1234.56789000", "50010.00", "9876.54321000"));

            var ticker = capturedTickers.get(0);
            assertEquals(new BigDecimal("1234.56789000"), ticker.bidQty());
            assertEquals(new BigDecimal("9876.54321000"), ticker.askQty());
        }

        @Test
        @DisplayName("Should ignore messages without stream field")
        void shouldIgnoreMessageWithoutStream() {
            var msg = MAPPER.createObjectNode()
                    .set("data", MAPPER.createObjectNode().put("e", "bookTicker"));

            adapter.handleMessage(msg.toString());

            assertTrue(capturedTickers.isEmpty());
        }

        @Test
        @DisplayName("Should ignore messages without data field")
        void shouldIgnoreMessageWithoutData() {
            var msg = MAPPER.createObjectNode()
                    .put("stream", "btcusdt@bookTicker");

            adapter.handleMessage(msg.toString());

            assertTrue(capturedTickers.isEmpty());
        }

        @Test
        @DisplayName("Should skip non-bookTicker streams")
        void shouldSkipNonBookTickerStreams() {
            var data = MAPPER.createObjectNode()
                    .put("e", "aggTrade")
                    .put("s", "BTCUSDT")
                    .put("p", "50000.00");
            var msg = MAPPER.createObjectNode()
                    .put("stream", "btcusdt@aggTrade")
                    .set("data", data);

            adapter.handleMessage(msg.toString());

            assertTrue(capturedTickers.isEmpty());
        }

        @Test
        @DisplayName("Should create OrderBook with correct data")
        void shouldCreateOrderBook() {
            adapter.handleMessage(sampleBookTickerMessage("btcusdt@bookTicker", "BTCUSDT",
                    "50000.00", "1.5", "50010.00", "2.0"));

            var orderBook = capturedOrderBooks.get(0);
            assertEquals(ExchangeEnum.BINANCE, orderBook.exchange());
            assertEquals("BTCUSDT", orderBook.symbol());
            assertEquals(1, orderBook.bids().size());
            assertEquals(new BigDecimal("50000.00"), orderBook.bids().get(0).price());
            assertEquals(new BigDecimal("1.5"), orderBook.bids().get(0).quantity());
            assertEquals(1, orderBook.asks().size());
            assertEquals(new BigDecimal("50010.00"), orderBook.asks().get(0).price());
            assertEquals(new BigDecimal("2.0"), orderBook.asks().get(0).quantity());
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
        @DisplayName("Should clear handlers")
        void shouldClearHandlers() {
            adapter.clearHandlers();
            adapter.handleMessage(sampleBookTickerMessage("btcusdt@bookTicker", "BTCUSDT",
                    "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(capturedTickers.isEmpty());
            assertTrue(capturedOrderBooks.isEmpty());
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

            adapter.handleMessage(sampleBookTickerMessage("btcusdt@bookTicker", "BTCUSDT",
                    "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }

    // --- Helpers ---

    private String sampleBookTickerMessage(String stream, String symbol,
                                           String bidPrice, String bidQty,
                                           String askPrice, String askQty) {
        var data = MAPPER.createObjectNode()
                .put("e", "bookTicker")
                .put("u", 400900217L)
                .put("s", symbol)
                .put("b", bidPrice)
                .put("B", bidQty)
                .put("a", askPrice)
                .put("A", askQty)
                .put("E", System.currentTimeMillis())
                .put("T", System.currentTimeMillis());

        var root = MAPPER.createObjectNode()
                .put("stream", stream)
                .set("data", data);

        return root.toString();
    }
}
