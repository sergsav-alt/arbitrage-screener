package com.arbitrage.screener.core;

import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SpreadCalculatorTest {

    private SpreadCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SpreadCalculator();
    }

    @Nested
    @DisplayName("Spread percent calculation")
    class SpreadPercent {

        @Test
        @DisplayName("Should calculate positive spread")
        void shouldCalculatePositiveSpread() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00");

            var spread = calculator.calculateSpreadPercent(buyTicker, sellTicker);

            // (50010 - 50005) / 50005 * 100 ≈ 0.01%
            assertTrue(spread.compareTo(BigDecimal.ZERO) > 0);
            assertEquals(0, spread.compareTo(new BigDecimal("0.0100")));
        }

        @Test
        @DisplayName("Should calculate negative spread (unprofitable)")
        void shouldCalculateNegativeSpread() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "49900.00", "49905.00");

            var spread = calculator.calculateSpreadPercent(buyTicker, sellTicker);

            // (49900 - 50005) / 50005 * 100 = negative
            assertTrue(spread.compareTo(BigDecimal.ZERO) < 0);
        }

        @Test
        @DisplayName("Should calculate zero spread (same prices)")
        void shouldCalculateZeroSpread() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50000.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50000.00", "50000.00");

            var spread = calculator.calculateSpreadPercent(buyTicker, sellTicker);

            assertEquals(0, BigDecimal.ZERO.compareTo(spread));
        }

        @Test
        @DisplayName("Should handle zero buy price gracefully")
        void shouldHandleZeroBuyPrice() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "0", "0");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50000.00", "50001.00");

            var spread = calculator.calculateSpreadPercent(buyTicker, sellTicker);

            assertEquals(0, BigDecimal.ZERO.compareTo(spread));
        }

        @ParameterizedTest
        @DisplayName("Should calculate spread for various price scenarios")
        @CsvSource({
                "50000, 50010, 0.0200",   // 0.02% spread
                "1000, 1001, 0.1000",      // 0.1% spread
                "100, 101, 1.0000",        // 1% spread
                "50000, 49500, -1.0000",   // -1% spread (unprofitable)
        })
        void shouldCalculateVariousSpreads(String buyAsk, String sellBid, String expected) {
            var buyTicker = new Ticker(ExchangeEnum.MEXC, "BTCUSDT",
                    new BigDecimal("0"), new BigDecimal("1"),
                    new BigDecimal(buyAsk), new BigDecimal("1"),
                    Instant.now());
            var sellTicker = new Ticker(ExchangeEnum.BINGX, "BTCUSDT",
                    new BigDecimal(sellBid), new BigDecimal("1"),
                    new BigDecimal("0"), new BigDecimal("1"),
                    Instant.now());

            var spread = calculator.calculateSpreadPercent(buyTicker, sellTicker);
            assertEquals(new BigDecimal(expected), spread);
        }
    }

    @Nested
    @DisplayName("Absolute spread")
    class AbsoluteSpread {

        @Test
        @DisplayName("Should calculate absolute positive spread")
        void shouldCalculateAbsolutePositiveSpread() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00");

            var spread = calculator.calculateAbsoluteSpread(buyTicker, sellTicker);

            // 50010 - 50005 = 5
            assertEquals(0, new BigDecimal("5.00").compareTo(spread));
        }

        @Test
        @DisplayName("Should calculate negative absolute spread")
        void shouldCalculateNegativeAbsoluteSpread() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "49900.00", "49905.00");

            var spread = calculator.calculateAbsoluteSpread(buyTicker, sellTicker);

            assertTrue(spread.compareTo(BigDecimal.ZERO) < 0);
        }
    }

    @Nested
    @DisplayName("Max volume")
    class MaxVolume {

        @Test
        @DisplayName("Should return min of ask and bid quantities")
        void shouldReturnMinVolume() {
            var buyTicker = new Ticker(ExchangeEnum.MEXC, "BTCUSDT",
                    new BigDecimal("0"), new BigDecimal("100"),
                    new BigDecimal("50000"), new BigDecimal("2.5"),
                    Instant.now());
            var sellTicker = new Ticker(ExchangeEnum.BINGX, "BTCUSDT",
                    new BigDecimal("50010"), new BigDecimal("1.0"),
                    new BigDecimal("0"), new BigDecimal("100"),
                    Instant.now());

            var volume = calculator.calculateMaxVolume(buyTicker, sellTicker);

            // min(2.5, 1.0) = 1.0
            assertEquals(0, new BigDecimal("1.0").compareTo(volume));
        }
    }

    @Nested
    @DisplayName("Profitability check")
    class Profitability {

        @Test
        @DisplayName("Should be profitable when spread > 0")
        void shouldBeProfitable() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00");

            assertTrue(calculator.isProfitable(buyTicker, sellTicker));
        }

        @Test
        @DisplayName("Should not be profitable when spread <= 0")
        void shouldNotBeProfitable() {
            var buyTicker = createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50000.00");
            var sellTicker = createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50000.00", "50000.00");

            assertFalse(calculator.isProfitable(buyTicker, sellTicker));
        }
    }

    // --- Helpers ---

    private Ticker createTicker(ExchangeEnum exchange, String symbol, String bidPrice, String askPrice) {
        return new Ticker(exchange, symbol,
                new BigDecimal(bidPrice), new BigDecimal("1.0"),
                new BigDecimal(askPrice), new BigDecimal("1.0"),
                Instant.now());
    }
}
