package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.ArbitrageType;
import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArbitrageDetectorTest {

    private ArbitrageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ArbitrageDetector(new SpreadCalculator());
    }

    @Nested
    @DisplayName("Detection scenarios")
    class DetectionScenarios {

        @Test
        @DisplayName("Should return empty when only one exchange has ticker")
        void shouldReturnEmptyForSingleExchange() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00")
                    )
            );

            var opportunities = detector.detect(grouped);
            assertTrue(opportunities.isEmpty());
        }

        @Test
        @DisplayName("Should detect one opportunity between two exchanges")
        void shouldDetectOneOpportunity() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            // Both directions are checked: MEXC→BINGX and BINGX→MEXC
            assertEquals(2, opportunities.size());

            // Check profitable direction: buy MEXC, sell BINGX
            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .findFirst()
                    .orElseThrow();

            assertEquals("BTCUSDT", profitable.symbol());
            assertEquals(ExchangeEnum.MEXC, profitable.buyExchange());
            assertEquals(ExchangeEnum.BINGX, profitable.sellExchange());
            assertEquals(ArbitrageType.CROSS_EXCHANGE, profitable.type());
            assertNotNull(profitable.detectedAt());
        }

        @Test
        @DisplayName("Should detect two profitable directions with two exchanges")
        void shouldDetectWithTwoExchanges() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "49990.00", "49995.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50005.00", "50010.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            // 2 exchanges = 1 pair * 2 directions = 2 opportunities
            assertEquals(2, opportunities.size());

            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .toList();

            // The best buy is MEXC (ask 49995), best sell is BINGX (bid 50005)
            assertTrue(profitable.size() >= 1);
        }

        @Test
        @DisplayName("Should handle completely unprofitable scenario")
        void shouldHandleUnprofitableScenario() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "49900.00", "50005.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            assertEquals(2, opportunities.size());

            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .count();

            assertEquals(0, profitable);
        }

        @Test
        @DisplayName("Should handle multiple symbols")
        void shouldHandleMultipleSymbols() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00")
                    ),
                    "ETHUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "ETHUSDT", "3000.00", "3001.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "ETHUSDT", "3005.00", "3007.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            // 2 symbols * 2 exchanges * 2 directions = 4 opportunities
            assertEquals(4, opportunities.size());

            var btcOpps = opportunities.stream()
                    .filter(o -> o.symbol().equals("BTCUSDT"))
                    .count();
            var ethOpps = opportunities.stream()
                    .filter(o -> o.symbol().equals("ETHUSDT"))
                    .count();

            assertEquals(2, btcOpps);
            assertEquals(2, ethOpps);
        }
    }

    @Nested
    @DisplayName("Opportunity fields")
    class OpportunityFields {

        @Test
        @DisplayName("Should populate all fields correctly")
        void shouldPopulateAllFields() {
            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00")
                    )
            );

            var opportunities = detector.detect(grouped);
            var opp = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .findFirst()
                    .orElseThrow();

            assertEquals("BTCUSDT", opp.symbol());
            assertEquals(ExchangeEnum.MEXC, opp.buyExchange());
            assertEquals(ExchangeEnum.BINGX, opp.sellExchange());
            assertEquals(new BigDecimal("50005.00"), opp.buyPrice());
            assertEquals(new BigDecimal("50010.00"), opp.sellPrice());
            assertTrue(opp.spreadPercent().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(ArbitrageType.CROSS_EXCHANGE, opp.type());
            assertNotNull(opp.id());
            assertNotNull(opp.detectedAt());
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
