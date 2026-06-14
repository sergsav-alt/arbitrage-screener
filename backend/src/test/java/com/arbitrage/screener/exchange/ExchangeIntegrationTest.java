package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.model.TickerCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест: проверяет совместную работу адаптеров трёх бирж,
 * нормализацию и хранение тикеров в общем кэше.
 */
class ExchangeIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExchangeAdapterFactory factory;
    private TickerCache cache;
    private PriceNormalizer normalizer;
    private List<Ticker> allTickers;

    @BeforeEach
    void setUp() {
        factory = new ExchangeAdapterFactory();
        cache = new TickerCache();
        normalizer = new PriceNormalizer(cache);
        allTickers = new ArrayList<>();

        registerHandlers(ExchangeEnum.BINGX);
        registerHandlers(ExchangeEnum.MEXC);
    }

    private void registerHandlers(ExchangeEnum exchange) {
        var adapter = factory.getAdapter(exchange);
        adapter.onTicker(ticker -> {
            cache.put(ticker);
            allTickers.add(ticker);
        });
    }

    @Test
    @DisplayName("Should create two distinct adapters")
    void shouldCreateTwoAdapters() {
        var bingx = factory.getAdapter(ExchangeEnum.BINGX);
        var mexc = factory.getAdapter(ExchangeEnum.MEXC);

        assertNotSame(bingx, mexc);

        assertEquals(ExchangeEnum.BINGX, bingx.getExchange());
        assertEquals(ExchangeEnum.MEXC, mexc.getExchange());
    }

    @Test
    @DisplayName("Should route tickers from all exchanges to common cache")
    void shouldRouteToCommonCache() {
        simulateBingXTicker("BTC-USDT", "50005.00", "50015.00");
        simulateMexcTicker("BTCUSDT", "49995.00", "50005.00");

        assertEquals(2, allTickers.size());
        assertEquals(2, cache.size());

        var grouped = normalizer.groupBySymbol();
        assertEquals(1, grouped.size());

        var btcTickers = grouped.get("BTCUSDT");
        assertEquals(2, btcTickers.size());
        assertTrue(btcTickers.containsKey(ExchangeEnum.BINGX));
        assertTrue(btcTickers.containsKey(ExchangeEnum.MEXC));
    }

    @Test
    @DisplayName("Should normalize different symbol formats to common key")
    void shouldNormalizeSymbolFormats() {
        simulateBingXTicker("ETH-USDT", "3002.00", "3003.00");
        simulateMexcTicker("ETHUSDT", "2999.00", "3000.00");

        var grouped = normalizer.groupBySymbol();
        assertEquals(1, grouped.size());
        assertNotNull(grouped.get("ETHUSDT"));

        var tickers = grouped.get("ETHUSDT");
        assertEquals(2, tickers.size());
    }

    @Test
    @DisplayName("Should handle multiple symbols across exchanges")
    void shouldHandleMultipleSymbols() {
        simulateBingXTicker("BTC-USDT", "50005.00", "50015.00");
        simulateMexcTicker("BTCUSDT", "49995.00", "50005.00");

        var grouped = normalizer.groupBySymbol();

        assertEquals(1, grouped.size());

        var btcTickers = grouped.get("BTCUSDT");
        assertEquals(2, btcTickers.size());
    }

    @Test
    @DisplayName("Should disconnect all adapters")
    void shouldDisconnectAll() {
        factory.disconnectAll();

        assertFalse(factory.getAdapter(ExchangeEnum.BINGX).isConnected());
        assertFalse(factory.getAdapter(ExchangeEnum.MEXC).isConnected());
    }

    @Test
    @DisplayName("Should handle empty cache")
    void shouldHandleEmptyCache() {
        assertTrue(cache.getAll().isEmpty());
        assertTrue(normalizer.groupBySymbol().isEmpty());
        assertEquals(0, cache.size());
    }

    // --- Message Simulation Helpers ---

    private void simulateBingXTicker(String symbol, String bidPrice, String askPrice) {
        var bingx = (com.arbitrage.screener.exchange.bingx.BingXAdapter) factory.getAdapter(ExchangeEnum.BINGX);
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
        bingx.handleMessage(root.toString());
    }

    private void simulateMexcTicker(String symbol, String bidPrice, String askPrice) {
        var mexc = (com.arbitrage.screener.exchange.mexc.MexcAdapter) factory.getAdapter(ExchangeEnum.MEXC);
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
        mexc.handleMessage(root.toString());
    }
}
