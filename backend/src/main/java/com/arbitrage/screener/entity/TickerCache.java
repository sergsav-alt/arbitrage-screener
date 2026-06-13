package com.arbitrage.screener.entity;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище последних тикеров с бирж.
 * Ключ: exchange:symbol (например, "BINANCE:BTCBUSDT").
 */
public class TickerCache {

    private final Map<String, Ticker> cache = new ConcurrentHashMap<>();

    public void put(Ticker ticker) {
        var key = buildKey(ticker.exchange(), ticker.symbol());
        cache.put(key, ticker);
    }

    public Ticker get(ExchangeEnum exchange, String symbol) {
        return cache.get(buildKey(exchange, symbol));
    }

    public Collection<Ticker> getAll() {
        return cache.values();
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    public Instant getLastUpdateTime() {
        return cache.values().stream()
                .map(Ticker::timestamp)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private String buildKey(ExchangeEnum exchange, String symbol) {
        return exchange.name() + ":" + symbol.toUpperCase();
    }
}
