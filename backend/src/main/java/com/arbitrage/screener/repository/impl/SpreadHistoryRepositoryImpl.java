package com.arbitrage.screener.repository.impl;

import com.arbitrage.screener.entity.SpreadHistory;
import com.arbitrage.screener.repository.SpreadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.arbitrage.screener.repository.table.JooqTables.*;

@Repository
@RequiredArgsConstructor
public class SpreadHistoryRepositoryImpl implements SpreadHistoryRepository {

    private final DSLContext dsl;

    public void saveAll(List<SpreadHistory> histories) {
        var inserts = histories.stream()
                .map(h -> dsl.insertInto(SPREAD_HISTORY)
                        .set(SH_SYMBOL, h.getSymbol())
                        .set(SH_BUY_EXCHANGE, h.getBuyExchange())
                        .set(SH_SELL_EXCHANGE, h.getSellExchange())
                        .set(SH_BUY_PRICE, h.getBuyPrice())
                        .set(SH_SELL_PRICE, h.getSellPrice())
                        .set(SH_SPREAD_PERCENT, h.getSpreadPercent())
                        .set(SH_NET_PROFIT, h.getNetProfit())
                        .set(SH_MAX_VOLUME, h.getMaxVolume())
                        .set(SH_RECORDED_AT, h.getRecordedAt()))
                .toList();

        dsl.batch(inserts).execute();
    }

    public List<SpreadHistory> findBySymbolAndRecordedAtBetween(String symbol, Instant from, Instant to) {
        return dsl.select(
                        SH_ID, SH_SYMBOL, SH_BUY_EXCHANGE, SH_SELL_EXCHANGE,
                        SH_BUY_PRICE, SH_SELL_PRICE, SH_SPREAD_PERCENT,
                        SH_NET_PROFIT, SH_MAX_VOLUME, SH_RECORDED_AT)
                .from(SPREAD_HISTORY)
                .where(SH_SYMBOL.eq(symbol)
                        .and(SH_RECORDED_AT.between(from, to)))
                .orderBy(SH_RECORDED_AT.asc())
                .fetch(r -> SpreadHistory.builder()
                        .id(r.get(SH_ID))
                        .symbol(r.get(SH_SYMBOL))
                        .buyExchange(r.get(SH_BUY_EXCHANGE))
                        .sellExchange(r.get(SH_SELL_EXCHANGE))
                        .buyPrice(r.get(SH_BUY_PRICE))
                        .sellPrice(r.get(SH_SELL_PRICE))
                        .spreadPercent(r.get(SH_SPREAD_PERCENT))
                        .netProfit(r.get(SH_NET_PROFIT))
                        .maxVolume(r.get(SH_MAX_VOLUME))
                        .recordedAt(r.get(SH_RECORDED_AT))
                        .build());
    }

    public List<SpreadHistory> findByRecordedAtBetween(Instant from, Instant to) {
        return dsl.select(
                        SH_ID, SH_SYMBOL, SH_BUY_EXCHANGE, SH_SELL_EXCHANGE,
                        SH_BUY_PRICE, SH_SELL_PRICE, SH_SPREAD_PERCENT,
                        SH_NET_PROFIT, SH_MAX_VOLUME, SH_RECORDED_AT)
                .from(SPREAD_HISTORY)
                .where(SH_RECORDED_AT.between(from, to))
                .orderBy(SH_RECORDED_AT.asc())
                .fetch(r -> SpreadHistory.builder()
                        .id(r.get(SH_ID))
                        .symbol(r.get(SH_SYMBOL))
                        .buyExchange(r.get(SH_BUY_EXCHANGE))
                        .sellExchange(r.get(SH_SELL_EXCHANGE))
                        .buyPrice(r.get(SH_BUY_PRICE))
                        .sellPrice(r.get(SH_SELL_PRICE))
                        .spreadPercent(r.get(SH_SPREAD_PERCENT))
                        .netProfit(r.get(SH_NET_PROFIT))
                        .maxVolume(r.get(SH_MAX_VOLUME))
                        .recordedAt(r.get(SH_RECORDED_AT))
                        .build());
    }

    public void deleteByRecordedAtBefore(Instant threshold) {
        dsl.deleteFrom(SPREAD_HISTORY)
                .where(SH_RECORDED_AT.lt(threshold))
                .execute();
    }
}
