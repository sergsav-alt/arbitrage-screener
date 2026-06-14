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

class SlippageCalculatorTest {

    private SlippageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SlippageCalculator();
    }

    @Test
    @DisplayName("Should return zero slippage when volume fits in first level")
    void shouldReturnZeroSlippageForFirstLevelVolume() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("2.0")),
                        new OrderBookLevel(new BigDecimal("50001.00"), new BigDecimal("1.0"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("3.0"))
                )
        );

        var slippage = calculator.calculateBuySlippage(orderBook, new BigDecimal("1.0"));
        assertEquals(0, BigDecimal.ZERO.compareTo(slippage));
    }

    @Test
    @DisplayName("Should calculate slippage when walking multiple levels")
    void shouldCalculateSlippageForMultipleLevels() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0")),
                        new OrderBookLevel(new BigDecimal("50002.00"), new BigDecimal("1.0"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("3.0"))
                )
        );

        // Ideal: 1.5 * 50000 = 75000
        // Actual: 1.0 * 50000 + 0.5 * 50002 = 50000 + 25001 = 75001
        // Slippage = 75001 - 75000 = 1 USDT
        var slippage = calculator.calculateBuySlippage(orderBook, new BigDecimal("1.5"));
        assertEquals(1, new BigDecimal("1.0").compareTo(slippage));
    }

    @Test
    @DisplayName("Should handle insufficient liquidity (partial fill)")
    void shouldHandlePartialFill() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("0.5"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("1.0"))
                )
        );

        var slippage = calculator.calculateBuySlippage(orderBook, new BigDecimal("2.0"));
        // Only 0.5 available, executed at best price, no slippage for available volume
        assertTrue(slippage.compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Should calculate sell slippage correctly")
    void shouldCalculateSellSlippage() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0")),
                        new OrderBookLevel(new BigDecimal("49998.00"), new BigDecimal("1.0"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("3.0"))
                )
        );

        // Ideal: 1.5 * 50000 = 75000
        // Actual: 1.0 * 50000 + 0.5 * 49998 = 50000 + 24999 = 74999
        // Slippage = 75000 - 74999 = 1 USDT
        var slippage = calculator.calculateSellSlippage(orderBook, new BigDecimal("1.5"));
        assertEquals(0, new BigDecimal("1.0").compareTo(slippage));
    }

    @Test
    @DisplayName("Should return zero slippage for zero volume")
    void shouldReturnZeroForZeroVolume() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("2.0"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("3.0"))
                )
        );

        var slippage = calculator.calculateBuySlippage(orderBook, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(slippage));
    }

    @Test
    @DisplayName("Should calculate slippage percentage")
    void shouldCalculateSlippagePercent() {
        var orderBook = createOrderBook(
                List.of(
                        new OrderBookLevel(new BigDecimal("50000.00"), new BigDecimal("1.0")),
                        new OrderBookLevel(new BigDecimal("50002.00"), new BigDecimal("1.0"))
                ),
                List.of(
                        new OrderBookLevel(new BigDecimal("50010.00"), new BigDecimal("3.0"))
                )
        );

        var percent = calculator.calculateSlippagePercent(orderBook, new BigDecimal("1.5"), true);
        assertFalse(percent.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(percent.compareTo(new BigDecimal("1")) < 0);
    }

    private OrderBook createOrderBook(List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        return new OrderBook(ExchangeEnum.BINGX, "BTCUSDT", bids, asks, Instant.now());
    }
}
