package com.arbitrage.screener.cache;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.Deal;
import lombok.Getter;
import org.springframework.stereotype.Component;

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
@Component
public class DealCache {

    private final Map<String, Deal> cache = new ConcurrentHashMap<>();
    @Getter
    private final long ttlSeconds;

    public DealCache() {
        this(30);
    }

    public DealCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public void put(Deal deal) {
        var key = buildKey(deal.exchange(), deal.symbol());
        cache.put(key, deal);
    }

    public Deal get(ExchangeEnum exchange, String symbol) {
        var deal = cache.get(buildKey(exchange, symbol));
        if (deal != null && isExpired(deal)) {
            return null;
        }
        return deal;
    }

    public Collection<Deal> getAll() {
        return cache.values();
    }

    /**
     * Возвращает только неистёкшие тикеры.
     */
    public List<Deal> getFresh() {
        var cutoff = Instant.now().minusSeconds(ttlSeconds);
        return cache.values().stream()
                .filter(t -> Instant.ofEpochMilli(t.timestamp()).isAfter(cutoff))
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
        cache.values().removeIf(ticker -> Instant.ofEpochMilli(ticker.timestamp()).isBefore(cutoff));
    }

    public Instant getLastUpdateTime() {
        return cache.values().stream()
                .map(e -> Instant.ofEpochMilli(e.timestamp()))
                .max(Instant::compareTo)
                .orElse(null);
    }

    private boolean isExpired(Deal deal) {
        return Duration.between(Instant.ofEpochMilli(deal.timestamp()), Instant.now()).getSeconds() > ttlSeconds;
    }

    private String buildKey(ExchangeEnum exchange, String symbol) {
        return exchange.name() + ":" + symbol.toUpperCase();
    }
}
