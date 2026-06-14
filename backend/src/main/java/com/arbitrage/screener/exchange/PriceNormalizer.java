package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.model.TickerCache;

import java.util.*;

/**
 * Нормализатор цен: приводит тикеры от разных бирж к единому формату
 * и предоставляет сгруппированное представление для кроссбиржевого сравнения.
 */
public class PriceNormalizer {

    private final TickerCache tickerCache;

    public PriceNormalizer(TickerCache tickerCache) {
        this.tickerCache = tickerCache;
    }

    /**
     * Группирует тикеры по нормализованному символу.
     * Ключ — символ в формате BTCUSDT, значение — тикеры с разных бирж.
     */
    public Map<String, Map<ExchangeEnum, Ticker>> groupBySymbol() {
        var grouped = new TreeMap<String, Map<ExchangeEnum, Ticker>>();

        for (var ticker : tickerCache.getAll()) {
            var symbol = normalizeSymbol(ticker.symbol());
            grouped
                    .computeIfAbsent(symbol, k -> new EnumMap<>(ExchangeEnum.class))
                    .put(ticker.exchange(), ticker);
        }

        return Collections.unmodifiableMap(grouped);
    }

    /**
     * Возвращает тикеры для заданного символа со всех бирж.
     */
    public Map<ExchangeEnum, Ticker> getTickersForSymbol(String symbol) {
        var normalizedSymbol = normalizeSymbol(symbol);
        var result = new EnumMap<ExchangeEnum, Ticker>(ExchangeEnum.class);

        for (var ticker : tickerCache.getAll()) {
            if (normalizeSymbol(ticker.symbol()).equals(normalizedSymbol)) {
                result.put(ticker.exchange(), ticker);
            }
        }

        return result;
    }

    /**
     * Нормализует символ: удаляет дефисы, приводит к верхнему регистру.
     * BTC-USDT → BTCUSDT
     */
    private String normalizeSymbol(String symbol) {
        return symbol.replace("-", "").toUpperCase();
    }
}
