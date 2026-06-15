package com.arbitrage.screener.cache;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище стаканов заявок с бирж.
 * Ключ: exchange:symbol (например, "MEXC:BTCUSDT").
 * Поддерживает TTL — автоматическое удаление устаревших стаканов.
 */
@Component
public class OrderBookCache {

    private final Map<String, OrderBook> cache = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public OrderBookCache() {
        this(30);
    }

    public OrderBookCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public void put(OrderBook orderBook) {
        var key = buildKey(orderBook.exchange(), orderBook.symbol());
        cache.put(key, orderBook);
    }

    public Optional<OrderBook> get(ExchangeEnum exchange, String symbol) {
        var orderBook = cache.get(buildKey(exchange, symbol));
        if (orderBook != null && isExpired(orderBook)) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderBook);
    }

    public Collection<OrderBook> getAll() {
        return cache.values();
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    public void evictExpired() {
        var cutoff = Instant.now().minusSeconds(ttlSeconds);
        cache.values().removeIf(ob -> Instant.ofEpochMilli(ob.timestamp()).isBefore(cutoff));
    }

    private boolean isExpired(OrderBook orderBook) {
        return Duration.between(Instant.ofEpochMilli(orderBook.timestamp()), Instant.now()).getSeconds() > ttlSeconds;
    }

    private String buildKey(ExchangeEnum exchange, String symbol) {
        return exchange.name() + ":" + symbol.toUpperCase();
    }
}
