package com.arbitrage.screener.core;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookCacheTest {

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("Should put and get order book")
        void shouldPutAndGet() {
            var cache = new OrderBookCache();
            var orderBook = createOrderBook("BTCUSDT", "50000.00", "1.0");

            cache.put(orderBook);

            var retrieved = cache.get(ExchangeEnum.BINGX, "BTCUSDT");
            assertTrue(retrieved.isPresent());
            assertEquals("BTCUSDT", retrieved.get().symbol());
        }

        @Test
        @DisplayName("Should return empty for missing order book")
        void shouldReturnEmptyForMissing() {
            var cache = new OrderBookCache();
            assertTrue(cache.get(ExchangeEnum.BINGX, "BTCUSDT").isEmpty());
        }

        @Test
        @DisplayName("Should get all order books")
        void shouldGetAll() {
            var cache = new OrderBookCache();
            cache.put(createOrderBook("BTCUSDT", "50000.00", "1.0"));
            cache.put(createOrderBook("ETHUSDT", "3000.00", "10.0"));

            assertEquals(2, cache.size());
            assertEquals(2, cache.getAll().size());
        }

        @Test
        @DisplayName("Should clear all order books")
        void shouldClear() {
            var cache = new OrderBookCache();
            cache.put(createOrderBook("BTCUSDT", "50000.00", "1.0"));
            cache.clear();

            assertEquals(0, cache.size());
        }
    }

    @Nested
    @DisplayName("TTL eviction")
    class TtlEviction {

        @Test
        @DisplayName("Should return empty for expired order book")
        void shouldReturnEmptyForExpired() {
            var cache = new OrderBookCache(1);
            var expired = new OrderBook(
                    ExchangeEnum.BINGX, "BTCUSDT",
                    List.of(new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0"))),
                    List.of(new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("1.0"))),
                    Instant.now().minus(5, ChronoUnit.SECONDS)
            );

            cache.put(expired);
            assertTrue(cache.get(ExchangeEnum.BINGX, "BTCUSDT").isEmpty());
        }

        @Test
        @DisplayName("Should return fresh order book")
        void shouldReturnFresh() {
            var cache = new OrderBookCache(60);
            var fresh = createOrderBook("BTCUSDT", "50000.00", "1.0");

            cache.put(fresh);
            assertTrue(cache.get(ExchangeEnum.BINGX, "BTCUSDT").isPresent());
        }

        @Test
        @DisplayName("Should evict expired order books")
        void shouldEvictExpired() {
            var cache = new OrderBookCache(1);
            var expired = new OrderBook(
                    ExchangeEnum.BINGX, "BTCUSDT",
                    List.of(new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0"))),
                    List.of(new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("1.0"))),
                    Instant.now().minus(10, ChronoUnit.SECONDS)
            );
            var fresh = createOrderBook("ETHUSDT", "3000.00", "10.0");

            cache.put(expired);
            cache.put(fresh);
            assertEquals(2, cache.size());

            cache.evictExpired();
            assertEquals(1, cache.size());
            assertTrue(cache.get(ExchangeEnum.BINGX, "ETHUSDT").isPresent());
        }
    }

    private OrderBook createOrderBook(String symbol, String price, String quantity) {
        var p = new BigDecimal(price);
        var q = new BigDecimal(quantity);
        return new OrderBook(
                ExchangeEnum.BINGX, symbol,
                List.of(new OrderBookLevel(p, q)),
                List.of(new OrderBookLevel(p.add(new BigDecimal("1")), q)),
                Instant.now()
        );
    }
}
