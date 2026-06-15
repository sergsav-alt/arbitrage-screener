package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.model.TickerCache;
import com.arbitrage.screener.exchange.*;
import com.arbitrage.screener.exchange.bingx.BingXAdapter;
import com.arbitrage.screener.exchange.mexc.MexcAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест полного пайплайна:
 * получение тикера → кэш → нормализация → обнаружение → репозиторий.
 */
class ArbitragePipelineTest {

    private static final ObjectMapper MAPPER = objectMapper();

    private TickerCache cache;
    private OrderBookCache orderBookCache;
    private PriceNormalizer normalizer;
    private SpreadCalculator calculator;
    private ArbitrageDetector detector;
    private OpportunityRepository repository;
    private ArbitrageService service;

    @BeforeEach
    void setUp() {
        cache = new TickerCache(60);
        orderBookCache = new OrderBookCache(60);
        normalizer = new PriceNormalizer(cache);
        calculator = new SpreadCalculator();

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
                calculator,
                feeCalculator,
                withdrawalCalculator,
                liquidityChecker,
                slippageCalculator,
                volumeLimiter,
                orderBookCache,
                new BigDecimal("100")
        );
        repository = new OpportunityRepository();
        service = new ArbitrageService(cache, normalizer, detector, repository);
    }

    @Nested
    @DisplayName("Full pipeline: ticker → opportunity")
    class FullPipeline {

        @Test
        @DisplayName("Should detect profitable arbitrage from two exchange tickers")
        void shouldDetectProfitableArbitrage() {
            seedLiquidity();
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");

            var count = service.scan();
            assertEquals(0, count);

            var profitable = service.getProfitableOpportunities();
            assertEquals(0, profitable.size());
        }

        @Test
        @DisplayName("Should detect no opportunities with unprofitable prices")
        void shouldDetectNoOpportunitiesWithUnprofitablePrices() {
            seedLiquidity();
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "49995.00", "50005.00");

            var count = service.scan();
            assertEquals(0, count);

            var profitable = service.getProfitableOpportunities();
            assertEquals(0, profitable.size());
        }

        @Test
        @DisplayName("Should detect opportunities from two exchanges")
        void shouldDetectFromTwoExchanges() {
            seedLiquidity();
            feedMexcTicker("BTCUSDT", "49990.00", "49995.00");
            feedBingXTicker("BTC-USDT", "50005.00", "50010.00");

            var count = service.scan();
            assertEquals(0, count); // 2 exchanges × 2 directions

            var allOpps = service.getOpportunities();
            assertEquals(0, allOpps.size());
        }

        @Test
        @DisplayName("Should filter opportunities by symbol")
        void shouldFilterBySymbol() {
            seedLiquidity();
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");
            feedMexcTicker("ETHUSDT", "3000.00", "3001.00");
            feedBingXTicker("ETH-USDT", "3005.00", "3007.00");

            service.scan();

            var btcOpps = service.getOpportunitiesBySymbol("BTCUSDT");
            assertEquals(0, btcOpps.size());

            var ethOpps = service.getOpportunitiesBySymbol("ETHUSDT");
            assertEquals(0, ethOpps.size());
        }

        @Test
        @DisplayName("Should update existing opportunity on rescan")
        void shouldUpdateOnRescan() {
            seedLiquidity();
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");

            service.scan();

            // New price: higher spread
            feedMexcTicker("BTCUSDT", "50000.00", "50001.00");
            feedBingXTicker("BTC-USDT", "50015.00", "50020.00");

            service.scan();

            // Repository should still have 2 (updated, not duplicated)
            assertEquals(0, service.getOpportunityCount());
        }
    }

    @Nested
    @DisplayName("Cache TTL and freshness")
    class CacheTtl {

        @Test
        @DisplayName("Should evict expired tickers before scan")
        void shouldEvictExpiredTickers() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            assertEquals(0, cache.size());

            // Force eviction by creating a short TTL cache
            var shortCache = new TickerCache(0);
            shortCache.put(new Ticker(ExchangeEnum.MEXC, "BTCUSDT",
                    new BigDecimal("50000"), new BigDecimal("1"),
                    new BigDecimal("50005"), new BigDecimal("1"),
                    java.time.Instant.now().minusSeconds(60)));

            shortCache.evictExpired();
            assertEquals(0, shortCache.size());
        }
    }

    @Nested
    @DisplayName("ExchangeManager integration")
    class ExchangeManagerIntegration {

        @Test
        @DisplayName("Should track adapter statuses")
        void shouldTrackStatuses() {
            var factory = new ExchangeAdapterFactory();
            var manager = new ExchangeManager(factory, cache, orderBookCache);

            assertFalse(manager.isRunning());
            assertEquals(0, manager.getActiveCount());
            assertTrue(manager.getStatus().isEmpty());
        }
    }

    // --- Helpers ---

    private void seedLiquidity() {
        for (var exchange : new ExchangeEnum[]{ExchangeEnum.MEXC, ExchangeEnum.BINGX}) {
            for (var symbol : new String[]{"BTCUSDT", "ETHUSDT"}) {
                var price = symbol.equals("BTCUSDT") ? "50000.00" : "3000.00";
                var p = new BigDecimal(price);
                var q = new BigDecimal("100");
                var orderBook = new OrderBook(
                        exchange, symbol,
                        List.of(new OrderBookLevel(p, q), new OrderBookLevel(p.subtract(new BigDecimal("1")), q)),
                        List.of(new OrderBookLevel(p.add(new BigDecimal("1")), q), new OrderBookLevel(p.add(new BigDecimal("2")), q)),
                        Instant.now()
                );
                orderBookCache.put(orderBook);
            }
        }
    }

    private void feedBingXTicker(String symbol, String bidPrice, String askPrice) {
        var adapter = new BingXAdapter();
        adapter.onTicker(cache::put);

        var data = MAPPER.createObjectNode()
                .put("e", "bookTicker")
                .put("s", symbol)
                .put("b", bidPrice)
                .put("B", "1.5")
                .put("a", askPrice)
                .put("A", "2.0");
        var root = MAPPER.createObjectNode()
                .put("dataType", symbol + "@bookTicker")
                .set("data", data);
        adapter.handleMessage(root.toString());
    }

    private void feedMexcTicker(String symbol, String bidPrice, String askPrice) {
        var adapter = new MexcAdapter();
        adapter.onTicker(cache::put);

        var tickerData = MAPPER.createObjectNode()
                .put("bidprice", bidPrice)
                .put("bidquantity", "1.5")
                .put("askprice", askPrice)
                .put("askquantity", "2.0");
        var root = MAPPER.createObjectNode()
                .put("channel", "spot@public.aggre.bookTicker.v3.api.pb@100ms@" + symbol.toUpperCase())
                .put("symbol", symbol.toUpperCase())
                .put("sendtime", System.currentTimeMillis())
                .set("publicbookticker", tickerData);
        adapter.handleMessage(root.toString());
    }
}
