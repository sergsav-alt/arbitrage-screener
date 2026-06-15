package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.Deal;
import com.arbitrage.screener.cache.DealCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.arbitrage.screener.util.FormatHelper.normalizeSymbol;

/**
 * Нормализатор цен: приводит сдедки от разных бирж к единому формату
 * и предоставляет сгруппированное представление для кроссбиржевого сравнения.
 */
@Component
@RequiredArgsConstructor
public class PriceNormalizer {

    private final DealCache dealCache;

    /**
     * Группирует сделки по нормализованному символу.
     * Ключ — символ в формате BTCUSDT, значение — сделки с разных бирж.
     */
    public Map<String, Map<ExchangeEnum, Deal>> groupBySymbol() {
        var grouped = new TreeMap<String, Map<ExchangeEnum, Deal>>();

        for (var deal : dealCache.getAll()) {
            var symbol = normalizeSymbol(deal.symbol());
            grouped
                    .computeIfAbsent(symbol, k -> new EnumMap<>(ExchangeEnum.class))
                    .put(deal.exchange(), deal);
        }

        return Collections.unmodifiableMap(grouped);
    }

    /**
     * Возвращает сделки для заданного символа со всех бирж.
     */
    public Map<ExchangeEnum, Deal> getDealsForSymbol(String symbol) {
        var normalizedSymbol = normalizeSymbol(symbol);
        var result = new EnumMap<ExchangeEnum, Deal>(ExchangeEnum.class);

        for (var deal : dealCache.getAll()) {
            if (normalizeSymbol(deal.symbol()).equals(normalizedSymbol)) {
                result.put(deal.exchange(), deal);
            }
        }
        return result;
    }
}
