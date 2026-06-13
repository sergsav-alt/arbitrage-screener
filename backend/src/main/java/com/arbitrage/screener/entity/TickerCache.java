package com.arbitrage.screener.entity;

import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище последних тикеров с бирж.
 * Ключ: exchange:symbol (например, "MEXC:BTCUSDT").
 * Поддерживает TTL — автоматическое удаление устаревших тикеров.
 */
public class TickerCache {

    private final Map<String, Ticker> cache = new ConcurrentHashMap<>();
    @Getter
    private final long ttlSeconds;

    public TickerCache() {
        this(30);
    }

    public TickerCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public void put(Ticker ticker) {
        var key = buildKey(ticker.exchange(), ticker.symbol());
        cache.put(key, ticker);
    }

    public Ticker get(ExchangeEnum exchange, String symbol) {
        var ticker = cache.get(buildKey(exchange, symbol));
        if (ticker != null && isExpired(ticker)) {
            return null;
        }
        return ticker;
    }

    public Collection<Ticker> getAll() {
        return cache.values();
    }

    /**
     * Возвращает только неистёкшие тикеры.
     */
    public List<Ticker> getFresh() {
        var cutoff = Instant.now().minusSeconds(ttlSeconds);
        return cache.values().stream()
                .filter(t -> t.timestamp().isAfter(cutoff))
                .toList();
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Удаляет тикеры, чей timestamp старше TTL.
     */
    public void evictExpired() {
        var cutoff = Instant.now().minusSeconds(ttlSeconds);
        cache.values().removeIf(ticker -> ticker.timestamp().isBefore(cutoff));
    }

    public Instant getLastUpdateTime() {
        return cache.values().stream()
                .map(Ticker::timestamp)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private boolean isExpired(Ticker ticker) {
        return Duration.between(ticker.timestamp(), Instant.now()).getSeconds() > ttlSeconds;
    }

    private String buildKey(ExchangeEnum exchange, String symbol) {
        return exchange.name() + ":" + symbol.toUpperCase();
    }
}
