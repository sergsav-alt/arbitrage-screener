package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.model.TickerCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PriceNormalizerTest {

    private TickerCache cache;
    private PriceNormalizer normalizer;

    @BeforeEach
    void setUp() {
        cache = new TickerCache();
        normalizer = new PriceNormalizer(cache);
    }

    @Test
    @DisplayName("Should group tickers by symbol")
    void shouldGroupBySymbol() {
        cache.put(createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00"));
        cache.put(createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00"));
        cache.put(createTicker(ExchangeEnum.MEXC, "ETHUSDT", "3000.00"));

        var grouped = normalizer.groupBySymbol();

        assertEquals(2, grouped.size());
        assertTrue(grouped.containsKey("BTCUSDT"));
        assertTrue(grouped.containsKey("ETHUSDT"));

        var btcTickers = grouped.get("BTCUSDT");
        assertEquals(2, btcTickers.size());
        assertTrue(btcTickers.containsKey(ExchangeEnum.MEXC));
        assertTrue(btcTickers.containsKey(ExchangeEnum.BINGX));
    }

    @Test
    @DisplayName("Should normalize BingX symbol format (BTC-USDT → BTCUSDT)")
    void shouldNormalizeBingXSymbol() {
        cache.put(createTicker(ExchangeEnum.BINGX, "BTC-USDT", "50010.00"));
        cache.put(createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00"));

        var grouped = normalizer.groupBySymbol();

        assertEquals(1, grouped.size());
        var btcTickers = grouped.get("BTCUSDT");
        assertEquals(2, btcTickers.size());
    }

    @Test
    @DisplayName("Should return tickers for specific symbol")
    void shouldGetTickersForSymbol() {
        cache.put(createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00"));
        cache.put(createTicker(ExchangeEnum.BINGX, "BTC-USDT", "50010.00"));
        cache.put(createTicker(ExchangeEnum.MEXC, "ETHUSDT", "3000.00"));

        var btcTickers = normalizer.getTickersForSymbol("BTCUSDT");
        assertEquals(2, btcTickers.size());

        var ethTickers = normalizer.getTickersForSymbol("ETHUSDT");
        assertEquals(1, ethTickers.size());
    }

    @Test
    @DisplayName("Should return empty for unknown symbol")
    void shouldReturnEmptyForUnknownSymbol() {
        cache.put(createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00"));

        var tickers = normalizer.getTickersForSymbol("UNKNOWN");
        assertTrue(tickers.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty cache")
    void shouldHandleEmptyCache() {
        var grouped = normalizer.groupBySymbol();
        assertTrue(grouped.isEmpty());
    }

    // --- Helpers ---

    private Ticker createTicker(ExchangeEnum exchange, String symbol, String bidPrice) {
        return new Ticker(
                exchange, symbol,
                new BigDecimal(bidPrice), new BigDecimal("1.0"),
                new BigDecimal("50010.00"), new BigDecimal("2.0"),
                Instant.now()
        );
    }
}
