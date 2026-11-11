package com.arbitrage.screener.cache;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.Deal;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище последних сделок с бирж.
 * Ключ: {@code exchange:symbol} (например, {@code "MEXC:BTCUSDT"}).
 * Поддерживает TTL: сделки старше {@code ttlSeconds} считаются устаревшими.
 *
 * <p>Сделки с отсутствующим ({@code null}) или неположительным timestamp считаются
 * невалидными: они никогда не отдаются как актуальные и удаляются при {@link #evictExpired()}.
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
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds must not be negative: " + ttlSeconds);
        }
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Кладёт сделку в кэш, перезаписывая предыдущую сделку по тому же ключу.
     *
     * @throws NullPointerException если {@code deal}, его биржа или символ равны {@code null}
     */
    public void put(Deal deal) {
        Objects.requireNonNull(deal, "deal");
        var key = buildKey(deal.exchange(), deal.symbol());
        cache.put(key, deal);
    }

    /**
     * Возвращает сделку, если она есть в кэше и ещё не устарела, иначе {@code null}.
     */
    public Deal get(ExchangeEnum exchange, String symbol) {
        var deal = cache.get(buildKey(exchange, symbol));
        if (deal == null || isExpired(deal, Instant.now())) {
            return null;
        }
        return deal;
    }

    /**
     * Возвращает неизменяемый снимок всех сделок в кэше (включая устаревшие).
     * Возвращённая коллекция не позволяет менять содержимое кэша.
     */
    public Collection<Deal> getAll() {
        return List.copyOf(cache.values());
    }

    /**
     * Возвращает только неустаревшие сделки.
     */
    public List<Deal> getFresh() {
        var now = Instant.now();
        return cache.values().stream()
                .filter(deal -> !isExpired(deal, now))
                .toList();
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Удаляет устаревшие сделки, а также сделки с невалидным timestamp.
     */
    public void evictExpired() {
        var now = Instant.now();
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    /**
     * Возвращает максимальный валидный timestamp среди сделок в кэше
     * или {@code null}, если валидных timestamp нет.
     */
    public Instant getLastUpdateTime() {
        return cache.values().stream()
                .map(Deal::timestamp)
                .filter(DealCache::isValidTimestamp)
                .map(Instant::ofEpochMilli)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Сделка устарела, если её timestamp невалиден или её возраст строго больше TTL.
     */
    private boolean isExpired(Deal deal, Instant now) {
        var timestamp = deal.timestamp();
        if (!isValidTimestamp(timestamp)) {
            return true;
        }
        return Instant.ofEpochMilli(timestamp).plusSeconds(ttlSeconds).isBefore(now);
    }

    /**
     * timestamp в миллисекундах должен присутствовать и быть положительным:
     * {@code 0} — заглушка парсеров при отсутствии времени события, такие данные непригодны.
     */
    private static boolean isValidTimestamp(Long timestamp) {
        return timestamp != null && timestamp > 0;
    }

    private String buildKey(ExchangeEnum exchange, String symbol) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        return exchange.name() + ":" + symbol.toUpperCase(Locale.ROOT);
    }
}
