package com.arbitrage.screener.exchange.mexc;

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

class MexcAdapterTest {

    private static final ObjectMapper MAPPER = objectMapper();

    private MexcAdapter adapter;
    private List<Ticker> capturedTickers;
    private List<OrderBook> capturedOrderBooks;

    @BeforeEach
    void setUp() {
        adapter = new MexcAdapter();
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
            adapter.handleMessage(sampleBookTickerMessage("BTCUSDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertEquals(1, capturedTickers.size());
            assertEquals(1, capturedOrderBooks.size());

            var ticker = capturedTickers.get(0);
            assertEquals(ExchangeEnum.MEXC, ticker.exchange());
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
            adapter.handleMessage(sampleBookTickerMessage("ETHUSDT", "3000.00", "10.0", "3001.00", "5.0"));

            var ticker = capturedTickers.get(0);
            assertEquals("ETHUSDT", ticker.symbol());
        }

        @Test
        @DisplayName("Should ignore subscription ack messages")
        void shouldIgnoreSubscriptionAck() {
            var ack = MAPPER.createObjectNode()
                    .put("code", 0)
                    .put("msg", "spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT");

            adapter.handleMessage(ack.toString());

            assertTrue(capturedTickers.isEmpty());
            assertTrue(capturedOrderBooks.isEmpty());
        }

        @Test
        @DisplayName("Should skip messages without publicbookticker")
        void shouldSkipMessageWithoutBookTicker() {
            var msg = MAPPER.createObjectNode()
                    .put("channel", "spot@public.miniTicker.v3.api.pb@BTCUSDT@UTC+8")
                    .put("symbol", "BTCUSDT")
                    .set("publicMiniTicker", MAPPER.createObjectNode()
                            .put("symbol", "BTCUSDT")
                            .put("price", "50000.00"));

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
        @DisplayName("Should return MEXC exchange")
        void shouldReturnMexcExchange() {
            assertEquals(ExchangeEnum.MEXC, adapter.getExchange());
        }

        @Test
        @DisplayName("Should clear handlers")
        void shouldClearHandlers() {
            adapter.clearHandlers();
            adapter.handleMessage(sampleBookTickerMessage("BTCUSDT", "50000.00", "1.5", "50010.00", "2.0"));

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

            adapter.handleMessage(sampleBookTickerMessage("BTCUSDT", "50000.00", "1.5", "50010.00", "2.0"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }

    // --- Helpers ---

    private String sampleBookTickerMessage(String symbol, String bidPrice, String bidQty, String askPrice, String askQty) {
        var tickerData = MAPPER.createObjectNode()
                .put("bidprice", bidPrice)
                .put("bidquantity", bidQty)
                .put("askprice", askPrice)
                .put("askquantity", askQty);

        var root = MAPPER.createObjectNode()
                .put("channel", "spot@public.aggre.bookTicker.v3.api.pb@100ms@" + symbol)
                .put("symbol", symbol)
                .put("sendtime", System.currentTimeMillis())
                .set("publicbookticker", tickerData);

        return root.toString();
    }
}
