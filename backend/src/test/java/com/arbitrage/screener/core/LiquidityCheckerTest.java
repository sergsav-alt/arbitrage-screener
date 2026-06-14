package com.arbitrage.screener.core;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiquidityCheckerTest {

    private OrderBookCache orderBookCache;
    private LiquidityChecker checker;

    @BeforeEach
    void setUp() {
        orderBookCache = new OrderBookCache(60);
        checker = new LiquidityChecker(orderBookCache);
    }

    @Test
    @DisplayName("Should confirm sufficient ask liquidity")
    void shouldHaveSufficientAskLiquidity() {
        var orderBook = new OrderBook(
                ExchangeEnum.BINGX, "BTCUSDT",
                List.of(new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0"))),
                List.of(
                        new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("0.5")),
                        new OrderBookLevel(new BigDecimal("50002.00"), new BigDecimal("0.5"))
                ),
                Instant.now()
        );
        orderBookCache.put(orderBook);

        assertTrue(checker.hasSufficientAskLiquidity(
                ExchangeEnum.BINGX, "BTCUSDT", new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("Should detect insufficient ask liquidity")
    void shouldDetectInsufficientAskLiquidity() {
        var orderBook = new OrderBook(
                ExchangeEnum.BINGX, "BTCUSDT",
                List.of(new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0"))),
                List.of(new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("0.1"))),
                Instant.now()
        );
        orderBookCache.put(orderBook);

        assertFalse(checker.hasSufficientAskLiquidity(
                ExchangeEnum.BINGX, "BTCUSDT", new BigDecimal("1.0")));
    }

    @Test
    @DisplayName("Should detect sufficient bid liquidity")
    void shouldHaveSufficientBidLiquidity() {
        var orderBook = new OrderBook(
                ExchangeEnum.BINGX, "BTCUSDT",
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("0.5")),
                        new OrderBookLevel(new BigDecimal("49999.00"), new BigDecimal("0.5"))
                ),
                List.of(new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("1.0"))),
                Instant.now()
        );
        orderBookCache.put(orderBook);

        assertTrue(checker.hasSufficientBidLiquidity(
                ExchangeEnum.BINGX, "BTCUSDT", new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("Should return false for missing order book")
    void shouldReturnFalseForMissingOrderBook() {
        assertFalse(checker.hasSufficientAskLiquidity(
                ExchangeEnum.BINGX, "BTCUSDT", new BigDecimal("1.0")));
    }

    @Test
    @DisplayName("Should return available volume for asks")
    void shouldReturnAvailableAskVolume() {
        var orderBook = new OrderBook(
                ExchangeEnum.BINGX, "BTCUSDT",
                List.of(new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0"))),
                List.of(
                        new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("0.3")),
                        new OrderBookLevel(new BigDecimal("50002.00"), new BigDecimal("0.7"))
                ),
                Instant.now()
        );
        orderBookCache.put(orderBook);

        var volume = checker.getAvailableVolume(ExchangeEnum.BINGX, "BTCUSDT", true);
        assertEquals(0, new BigDecimal("1.0").compareTo(volume));
    }
}
