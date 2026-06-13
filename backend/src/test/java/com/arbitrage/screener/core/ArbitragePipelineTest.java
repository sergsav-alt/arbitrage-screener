package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.*;
import com.arbitrage.screener.exchange.bingx.BingXAdapter;
import com.arbitrage.screener.exchange.mexc.MexcAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест полного пайплайна:
 * получение тикера → кэш → нормализация → обнаружение → репозиторий.
 */
class ArbitragePipelineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TickerCache cache;
    private PriceNormalizer normalizer;
    private SpreadCalculator calculator;
    private ArbitrageDetector detector;
    private OpportunityRepository repository;
    private ArbitrageService service;

    @BeforeEach
    void setUp() {
        cache = new TickerCache(60);
        normalizer = new PriceNormalizer(cache);
        calculator = new SpreadCalculator();
        detector = new ArbitrageDetector(calculator);
        repository = new OpportunityRepository();
        service = new ArbitrageService(cache, normalizer, detector, repository);
    }

    @Nested
    @DisplayName("Full pipeline: ticker → opportunity")
    class FullPipeline {

        @Test
        @DisplayName("Should detect profitable arbitrage from two exchange tickers")
        void shouldDetectProfitableArbitrage() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");

            var count = service.scan();
            assertEquals(2, count);

            var profitable = service.getProfitableOpportunities();
            assertEquals(1, profitable.size());

            var opp = profitable.iterator().next();
            assertEquals("BTCUSDT", opp.symbol());
            assertEquals(ExchangeEnum.MEXC, opp.buyExchange());
            assertEquals(ExchangeEnum.BINGX, opp.sellExchange());
            assertTrue(opp.spreadPercent().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        @DisplayName("Should detect no opportunities with unprofitable prices")
        void shouldDetectNoOpportunitiesWithUnprofitablePrices() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "49995.00", "50005.00");

            var count = service.scan();
            assertEquals(2, count);

            var profitable = service.getProfitableOpportunities();
            assertEquals(0, profitable.size());
        }

        @Test
        @DisplayName("Should detect opportunities from two exchanges")
        void shouldDetectFromTwoExchanges() {
            feedMexcTicker("BTCUSDT", "49990.00", "49995.00");
            feedBingXTicker("BTC-USDT", "50005.00", "50010.00");

            var count = service.scan();
            assertEquals(2, count); // 2 exchanges × 2 directions

            var allOpps = service.getOpportunities();
            assertEquals(2, allOpps.size());
        }

        @Test
        @DisplayName("Should filter opportunities by symbol")
        void shouldFilterBySymbol() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");
            feedMexcTicker("ETHUSDT", "3000.00", "3001.00");
            feedBingXTicker("ETH-USDT", "3005.00", "3007.00");

            service.scan();

            var btcOpps = service.getOpportunitiesBySymbol("BTCUSDT");
            assertEquals(2, btcOpps.size());

            var ethOpps = service.getOpportunitiesBySymbol("ETHUSDT");
            assertEquals(2, ethOpps.size());
        }

        @Test
        @DisplayName("Should update existing opportunity on rescan")
        void shouldUpdateOnRescan() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");
            feedBingXTicker("BTC-USDT", "50010.00", "50015.00");

            service.scan();

            // New price: higher spread
            feedMexcTicker("BTCUSDT", "50000.00", "50001.00");
            feedBingXTicker("BTC-USDT", "50015.00", "50020.00");

            service.scan();

            // Repository should still have 2 (updated, not duplicated)
            assertEquals(2, service.getOpportunityCount());
        }
    }

    @Nested
    @DisplayName("Cache TTL and freshness")
    class CacheTtl {

        @Test
        @DisplayName("Should evict expired tickers before scan")
        void shouldEvictExpiredTickers() {
            feedMexcTicker("BTCUSDT", "50000.00", "50005.00");

            assertEquals(1, cache.size());

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
            var manager = new ExchangeManager(factory, cache);

            assertFalse(manager.isRunning());
            assertEquals(0, manager.getActiveCount());
            assertTrue(manager.getStatus().isEmpty());
        }
    }

    // --- Helpers ---

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
