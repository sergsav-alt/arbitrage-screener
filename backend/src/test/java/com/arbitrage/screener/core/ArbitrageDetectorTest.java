package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.model.ArbitrageType;
import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.model.Ticker;
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
    private OrderBookCache orderBookCache;

    @BeforeEach
    void setUp() {
        orderBookCache = new OrderBookCache(60);

        var feeConfig = new FeeConfig();
        feeConfig.getTakerPercent().put("bingx", new BigDecimal("0.1"));
        feeConfig.getTakerPercent().put("mexc", new BigDecimal("0.1"));
        feeConfig.getWithdrawal().setDefaultUsdt(new BigDecimal("2.0"));

        var feeCalculator = new FeeCalculator(feeConfig);
        var withdrawalCalculator = new WithdrawalFeeCalculator(feeConfig);
        var liquidityChecker = new LiquidityChecker(orderBookCache);
        var slippageCalculator = new SlippageCalculator();
        var volumeLimiter = new VolumeLimiter(new BigDecimal("1"));

        detector = new ArbitrageDetector(
                new SpreadCalculator(),
                feeCalculator,
                withdrawalCalculator,
                liquidityChecker,
                slippageCalculator,
                volumeLimiter,
                orderBookCache,
                new BigDecimal("100") // default volume in USDT
        );
    }

    /**
     * Создаёт OrderBook с достаточной ликвидностью для тестов.
     */
    private void seedOrderBook(ExchangeEnum exchange, String symbol, String price, String quantity) {
        var p = new BigDecimal(price);
        var q = new BigDecimal(quantity);
        var orderBook = new OrderBook(
                exchange, symbol,
                List.of(
                        new OrderBookLevel(p, q),
                        new OrderBookLevel(p.subtract(new BigDecimal("1")), q)
                ),
                List.of(
                        new OrderBookLevel(p.add(new BigDecimal("1")), q),
                        new OrderBookLevel(p.add(new BigDecimal("2")), q)
                ),
                Instant.now()
        );
        orderBookCache.put(orderBook);
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
        @DisplayName("Should detect opportunity with sufficient liquidity")
        void shouldDetectWithSufficientLiquidity() {
            // Seed order books with enough liquidity
            seedOrderBook(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "100");

            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            // Both directions are checked, but liquidity might filter some
            assertFalse(opportunities.isEmpty());

            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .findFirst();
            assertTrue(profitable.isPresent(), "Should have at least one profitable opportunity");
        }

        @Test
        @DisplayName("Should detect opportunities with net profit less than gross spread due to fees")
        void shouldAccountForFees() {
            seedOrderBook(ExchangeEnum.MEXC, "BTCUSDT", "50005.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "100");

            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "50015.00")
                    )
            );

            var opportunities = detector.detect(grouped);
            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .findFirst();

            assertTrue(profitable.isPresent());
            var opp = profitable.get();

            // Net profit should be less than gross profit due to fees
            var absoluteSpread = opp.sellPrice().subtract(opp.buyPrice());
            assertTrue(opp.netProfit().compareTo(absoluteSpread) < 0,
                    "Net profit should be less than absolute spread due to fees");
        }

        @Test
        @DisplayName("Should handle completely unprofitable scenario")
        void shouldHandleUnprofitableScenario() {
            seedOrderBook(ExchangeEnum.MEXC, "BTCUSDT", "50005.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "BTCUSDT", "50005.00", "100");

            var grouped = Map.of(
                    "BTCUSDT", Map.of(
                            ExchangeEnum.MEXC,
                            createTicker(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "50005.00"),
                            ExchangeEnum.BINGX,
                            createTicker(ExchangeEnum.BINGX, "BTCUSDT", "49900.00", "50005.00")
                    )
            );

            var opportunities = detector.detect(grouped);

            var profitable = opportunities.stream()
                    .filter(o -> o.spreadPercent().signum() > 0)
                    .count();

            assertEquals(0, profitable);
        }

        @Test
        @DisplayName("Should handle multiple symbols")
        void shouldHandleMultipleSymbols() {
            seedOrderBook(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "100");
            seedOrderBook(ExchangeEnum.MEXC, "ETHUSDT", "3000.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "ETHUSDT", "3005.00", "100");

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

            var btcOpps = opportunities.stream()
                    .filter(o -> o.symbol().equals("BTCUSDT"))
                    .count();
            var ethOpps = opportunities.stream()
                    .filter(o -> o.symbol().equals("ETHUSDT"))
                    .count();

            assertTrue(btcOpps > 0);
            assertTrue(ethOpps > 0);
        }
    }

    @Nested
    @DisplayName("Opportunity fields")
    class OpportunityFields {

        @Test
        @DisplayName("Should populate all fields correctly including fees and slippage")
        void shouldPopulateAllFields() {
            seedOrderBook(ExchangeEnum.MEXC, "BTCUSDT", "50000.00", "100");
            seedOrderBook(ExchangeEnum.BINGX, "BTCUSDT", "50010.00", "100");

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
            assertNotNull(opp.netProfit());
            assertNotNull(opp.slippage());
            assertTrue(opp.maxVolume().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    // --- Helpers ---

    private Ticker createTicker(ExchangeEnum exchange, String symbol, String bidPrice, String askPrice) {
        return new Ticker(exchange, symbol,
                new BigDecimal(bidPrice), new BigDecimal("100"),
                new BigDecimal(askPrice), new BigDecimal("100"),
                Instant.now());
    }
}
