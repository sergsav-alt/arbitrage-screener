package com.arbitrage.screener.entity;

import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TickerCacheTest {

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("Should put and get ticker")
        void shouldPutAndGet() {
            var cache = new TickerCache();
            var ticker = createTicker("BTCUSDT", "50000.00");

            cache.put(ticker);

            var retrieved = cache.get(ExchangeEnum.BINANCE, "BTCUSDT");
            assertNotNull(retrieved);
            assertEquals("BTCUSDT", retrieved.symbol());
            assertEquals(new BigDecimal("50000.00"), retrieved.bidPrice());
        }

        @Test
        @DisplayName("Should return null for missing ticker")
        void shouldReturnNullForMissing() {
            var cache = new TickerCache();
            assertNull(cache.get(ExchangeEnum.BINANCE, "BTCUSDT"));
        }

        @Test
        @DisplayName("Should get all tickers")
        void shouldGetAll() {
            var cache = new TickerCache();
            cache.put(createTicker("BTCUSDT", "50000.00"));
            cache.put(createTicker("ETHUSDT", "3000.00"));

            assertEquals(2, cache.size());
            assertEquals(2, cache.getAll().size());
        }

        @Test
        @DisplayName("Should clear all tickers")
        void shouldClear() {
            var cache = new TickerCache();
            cache.put(createTicker("BTCUSDT", "50000.00"));
            cache.clear();

            assertEquals(0, cache.size());
            assertTrue(cache.getAll().isEmpty());
        }
    }

    @Nested
    @DisplayName("TTL eviction")
    class TtlEviction {

        @Test
        @DisplayName("Should return null for expired ticker")
        void shouldReturnNullForExpiredTicker() {
            var cache = new TickerCache(1);
            var expiredTicker = new Ticker(
                    ExchangeEnum.BINANCE, "BTCUSDT",
                    new BigDecimal("50000.00"), new BigDecimal("1.0"),
                    new BigDecimal("50010.00"), new BigDecimal("2.0"),
                    Instant.now().minus(5, ChronoUnit.SECONDS)
            );

            cache.put(expiredTicker);

            assertNull(cache.get(ExchangeEnum.BINANCE, "BTCUSDT"));
        }

        @Test
        @DisplayName("Should return fresh ticker")
        void shouldReturnFreshTicker() {
            var cache = new TickerCache(60);
            var freshTicker = createTicker("BTCUSDT", "50000.00");

            cache.put(freshTicker);

            assertNotNull(cache.get(ExchangeEnum.BINANCE, "BTCUSDT"));
        }

        @Test
        @DisplayName("Should evict expired tickers")
        void shouldEvictExpired() {
            var cache = new TickerCache(1);
            var expiredTicker = new Ticker(
                    ExchangeEnum.BINANCE, "BTCUSDT",
                    new BigDecimal("50000.00"), new BigDecimal("1.0"),
                    new BigDecimal("50010.00"), new BigDecimal("2.0"),
                    Instant.now().minus(10, ChronoUnit.SECONDS)
            );
            var freshTicker = createTicker("ETHUSDT", "3000.00");

            cache.put(expiredTicker);
            cache.put(freshTicker);

            assertEquals(2, cache.size());

            cache.evictExpired();

            assertEquals(1, cache.size());
            assertNotNull(cache.get(ExchangeEnum.BINANCE, "ETHUSDT"));
        }

        @Test
        @DisplayName("getFresh should return only non-expired tickers")
        void getFreshShouldFilterExpired() {
            var cache = new TickerCache(1);
            var expiredTicker = new Ticker(
                    ExchangeEnum.BINANCE, "BTCUSDT",
                    new BigDecimal("50000.00"), new BigDecimal("1.0"),
                    new BigDecimal("50010.00"), new BigDecimal("2.0"),
                    Instant.now().minus(10, ChronoUnit.SECONDS)
            );
            var freshTicker = createTicker("ETHUSDT", "3000.00");

            cache.put(expiredTicker);
            cache.put(freshTicker);

            var fresh = cache.getFresh();
            assertEquals(1, fresh.size());
            assertEquals("ETHUSDT", fresh.get(0).symbol());
        }

        @Test
        @DisplayName("Should track last update time")
        void shouldTrackLastUpdateTime() {
            var cache = new TickerCache();
            assertNull(cache.getLastUpdateTime());

            cache.put(createTicker("BTCUSDT", "50000.00"));
            assertNotNull(cache.getLastUpdateTime());
        }
    }

    @Nested
    @DisplayName("Key format")
    class KeyFormat {

        @Test
        @DisplayName("Should normalize symbols to uppercase in keys")
        void shouldNormalizeKey() {
            var cache = new TickerCache();
            var ticker = createTicker("btcusdt", "50000.00");

            cache.put(ticker);

            assertNotNull(cache.get(ExchangeEnum.BINANCE, "BTCUSDT"));
            assertNotNull(cache.get(ExchangeEnum.BINANCE, "btcusdt"));
        }
    }

    // --- Helpers ---

    private Ticker createTicker(String symbol, String bidPrice) {
        return new Ticker(
                ExchangeEnum.BINANCE, symbol,
                new BigDecimal(bidPrice), new BigDecimal("1.0"),
                new BigDecimal("50010.00"), new BigDecimal("2.0"),
                Instant.now()
        );
    }
}
