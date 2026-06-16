package com.arbitrage.screener.metric;

import com.arbitrage.screener.model.ArbitrageOpportunity;
import com.arbitrage.screener.exchange.ExchangeEnum;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное хранилище текущих арбитражных возможностей.
 * Ключ уникальности: symbol:buyExchange:sellExchange.
 * При повторном обнаружении — обновляет существующую запись.
 */
public class OpportunityRepository {

    private final Map<String, ArbitrageOpportunity> opportunities = new ConcurrentHashMap<>();

    /**
     * Сохраняет или обновляет арбитражную возможность.
     */
    public void save(ArbitrageOpportunity opportunity) {
        var key = buildKey(
                opportunity.symbol(),
                opportunity.buyExchange(),
                opportunity.sellExchange()
        );
        opportunities.put(key, opportunity);
    }

    /**
     * Возвращает все текущие возможности.
     */
    public Collection<ArbitrageOpportunity> getAll() {
        return Collections.unmodifiableCollection(opportunities.values());
    }

    /**
     * Возвращает возможности, отфильтрованные по символу.
     */
    public Collection<ArbitrageOpportunity> getBySymbol(String symbol) {
        var normalizedSymbol = symbol.toUpperCase();
        return opportunities.values().stream()
                .filter(o -> o.symbol().equalsIgnoreCase(normalizedSymbol))
                .toList();
    }

    /**
     * Возвращает только прибыльные возможности (spreadPercent > 0).
     */
    public Collection<ArbitrageOpportunity> getProfitable() {
        return opportunities.values().stream()
                .filter(o -> o.spreadPercent().signum() > 0)
                .toList();
    }

    public int size() {
        return opportunities.size();
    }

    public void clear() {
        opportunities.clear();
    }

    private String buildKey(String symbol, ExchangeEnum buyExchange, ExchangeEnum sellExchange) {
        return symbol.toUpperCase() + ":" + buyExchange.name() + ":" + sellExchange.name();
    }
}
