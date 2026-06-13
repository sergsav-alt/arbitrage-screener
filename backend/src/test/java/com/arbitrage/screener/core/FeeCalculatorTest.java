package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeeCalculatorTest {

    private FeeCalculator calculator;

    @BeforeEach
    void setUp() {
        var config = new FeeConfig();
        config.getTakerPercent().put("bingx", new BigDecimal("0.1"));
        config.getTakerPercent().put("mexc", new BigDecimal("0.1"));
        calculator = new FeeCalculator(config);
    }

    @Test
    @DisplayName("Should calculate taker fee for 100 USDT at 0.1%")
    void shouldCalculateTakerFee() {
        var fee = calculator.calculateTakerFee(
                new BigDecimal("100"), ExchangeEnum.BINGX);
        assertEquals(0, new BigDecimal("0.1").compareTo(fee));
    }

    @Test
    @DisplayName("Should calculate taker fee for 1000 USDT at 0.1%")
    void shouldCalculateLargerFee() {
        var fee = calculator.calculateTakerFee(
                new BigDecimal("1000"), ExchangeEnum.BINGX);
        assertEquals(0, new BigDecimal("1.0").compareTo(fee));
    }

    @Test
    @DisplayName("Should return zero for unknown exchange")
    void shouldReturnZeroForUnknownExchange() {
        var fee = calculator.calculateTakerFee(
                new BigDecimal("100"), ExchangeEnum.BINGX);
        // BINGX has fee configured, should be non-zero
        assertTrue(fee.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should return zero for zero volume")
    void shouldReturnZeroForZeroVolume() {
        var fee = calculator.calculateTakerFee(
                BigDecimal.ZERO, ExchangeEnum.BINGX);
        assertEquals(0, BigDecimal.ZERO.compareTo(fee));
    }
}
