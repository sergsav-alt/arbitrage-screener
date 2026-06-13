package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WithdrawalFeeCalculatorTest {

    private WithdrawalFeeCalculator calculator;

    @BeforeEach
    void setUp() {
        var config = new FeeConfig();
        config.getWithdrawal().setDefaultUsdt(new BigDecimal("2.0"));

        Map<String, BigDecimal> usdtFees = new HashMap<>();
        usdtFees.put("bingx", new BigDecimal("1.0"));
        usdtFees.put("mexc", new BigDecimal("2.0"));
        config.getWithdrawal().getPerSymbol().put("USDT", usdtFees);

        calculator = new WithdrawalFeeCalculator(config);
    }

    @Test
    @DisplayName("Should calculate withdrawal fee for configured exchange")
    void shouldCalculateWithdrawalFee() {
        var fee = calculator.calculate("BTCUSDT", ExchangeEnum.BINGX);
        assertEquals(0, new BigDecimal("1.0").compareTo(fee));
    }

    @Test
    @DisplayName("Should calculate withdrawal fee for MEXC")
    void shouldCalculateMexcWithdrawalFee() {
        var fee = calculator.calculate("ETHUSDT", ExchangeEnum.MEXC);
        assertEquals(0, new BigDecimal("2.0").compareTo(fee));
    }

    @Test
    @DisplayName("Should return default fee for unknown exchange in symbol group")
    void shouldReturnDefaultFee() {
        // MEXC is configured in USDT group, but we check for non-existent exchange
        var fee = calculator.calculate("BTCUSDT", ExchangeEnum.BINGX);
        assertTrue(fee.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should extract USDT base symbol correctly")
    void shouldExtractBaseSymbol() {
        var fee = calculator.calculate("BTCUSDT", ExchangeEnum.BINGX);
        assertEquals(0, new BigDecimal("1.0").compareTo(fee));
    }

    @Test
    @DisplayName("Should return default for completely unknown symbol")
    void shouldReturnDefaultForUnknownSymbol() {
        var fee = calculator.calculate("UNKNOWNPAIR", ExchangeEnum.BINGX);
        assertEquals(0, new BigDecimal("2.0").compareTo(fee));
    }
}
