package com.arbitrage.screener.cache;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.Deal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DealCacheTest {

    private static final long TTL_SECONDS = 60;

    private final DealCache cache = new DealCache(TTL_SECONDS);

    private static Deal deal(ExchangeEnum exchange, String symbol, Long timestamp) {
        return new Deal(
                exchange,
                symbol,
                new BigDecimal("50000.5"),
                new BigDecimal("0.25"),
                timestamp);
    }

    private static long freshTimestamp() {
        return Instant.now().minusSeconds(5).toEpochMilli();
    }

    private static long expiredTimestamp() {
        return Instant.now().minusSeconds(TTL_SECONDS * 2).toEpochMilli();
    }

    @Test
    void putAndGetNormalizeSymbolCase() {
        var deal = deal(ExchangeEnum.MEXC, "btcusdt", freshTimestamp());

        cache.put(deal);

        assertThat(cache.get(ExchangeEnum.MEXC, "BTCUSDT")).isSameAs(deal);
        assertThat(cache.get(ExchangeEnum.MEXC, "btcusdt")).isSameAs(deal);
        assertThat(cache.get(ExchangeEnum.BINGX, "BTCUSDT")).isNull();
    }

    @Test
    void getReturnsNullForExpiredDeal() {
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", expiredTimestamp()));

        assertThat(cache.get(ExchangeEnum.MEXC, "BTCUSDT")).isNull();
    }

    @Test
    void getReturnsNullForNullTimestampWithoutThrowing() {
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", null));

        assertThat(cache.get(ExchangeEnum.MEXC, "BTCUSDT")).isNull();
    }

    @Test
    void getReturnsNullForZeroTimestamp() {
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", 0L));

        assertThat(cache.get(ExchangeEnum.MEXC, "BTCUSDT")).isNull();
    }

    @Test
    void getFreshFiltersOutExpiredAndInvalidDeals() {
        var fresh = deal(ExchangeEnum.MEXC, "BTCUSDT", freshTimestamp());
        cache.put(fresh);
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", expiredTimestamp()));
        cache.put(deal(ExchangeEnum.BINANCE, "SOLUSDT", null));
        cache.put(deal(ExchangeEnum.BINANCE, "XRPUSDT", 0L));

        assertThat(cache.getFresh()).containsExactly(fresh);
    }

    @Test
    void getAllReturnsImmutableSnapshotIncludingExpiredDeals() {
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", freshTimestamp()));
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", expiredTimestamp()));

        var snapshot = cache.getAll();
        cache.put(deal(ExchangeEnum.BINANCE, "SOLUSDT", freshTimestamp()));

        assertThat(snapshot).hasSize(2);
        assertThat(cache.size()).isEqualTo(3);
        assertThatThrownBy(snapshot::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void evictExpiredRemovesExpiredAndInvalidDeals() {
        var fresh = deal(ExchangeEnum.MEXC, "BTCUSDT", freshTimestamp());
        cache.put(fresh);
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", expiredTimestamp()));
        cache.put(deal(ExchangeEnum.BINANCE, "SOLUSDT", null));
        cache.put(deal(ExchangeEnum.BINANCE, "XRPUSDT", 0L));

        cache.evictExpired();

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.getAll()).containsExactly(fresh);
    }

    @Test
    void getLastUpdateTimeReturnsMaxValidTimestamp() {
        var older = Instant.now().minusSeconds(20).toEpochMilli();
        var newer = Instant.now().minusSeconds(2).toEpochMilli();
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", older));
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", newer));

        assertThat(cache.getLastUpdateTime()).isEqualTo(Instant.ofEpochMilli(newer));
    }

    @Test
    void getLastUpdateTimeIgnoresInvalidTimestamps() {
        var valid = Instant.now().minusSeconds(10).toEpochMilli();
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", valid));
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", null));
        cache.put(deal(ExchangeEnum.BINANCE, "SOLUSDT", 0L));

        assertThat(cache.getLastUpdateTime()).isEqualTo(Instant.ofEpochMilli(valid));
    }

    @Test
    void getLastUpdateTimeReturnsNullWhenCacheHasNoValidTimestamps() {
        assertThat(cache.getLastUpdateTime()).isNull();

        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", null));
        cache.put(deal(ExchangeEnum.BINGX, "ETHUSDT", 0L));

        assertThat(cache.getLastUpdateTime()).isNull();
    }

    @Test
    void putOverwritesDealWithSameKey() {
        cache.put(deal(ExchangeEnum.MEXC, "BTCUSDT", freshTimestamp()));
        var replacement = deal(ExchangeEnum.MEXC, "btcusdt", freshTimestamp());

        cache.put(replacement);

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(ExchangeEnum.MEXC, "BTCUSDT")).isSameAs(replacement);
    }

    @Test
    void putRejectsNullDeal() {
        assertThatThrownBy(() -> cache.put(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void putRejectsNullSymbol() {
        var deal = deal(ExchangeEnum.MEXC, null, freshTimestamp());

        assertThatThrownBy(() -> cache.put(deal))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getRejectsNullSymbol() {
        assertThatThrownBy(() -> cache.get(ExchangeEnum.MEXC, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNegativeTtl() {
        assertThatThrownBy(() -> new DealCache(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttlSeconds");
    }
}
