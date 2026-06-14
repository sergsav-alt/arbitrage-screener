package com.arbitrage.screener.repository.impl;

import com.arbitrage.screener.entity.ArbitrageEvent;
import com.arbitrage.screener.repository.ArbitrageEventRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.arbitrage.screener.repository.table.JooqTables.*;

@Repository
@RequiredArgsConstructor
public class ArbitrageEventRepositoryImpl implements ArbitrageEventRepository {

    private final DSLContext dsl;

    public void saveAll(List<ArbitrageEvent> events) {
        var inserts = events.stream()
                .map(e -> dsl.insertInto(ARBITRAGE_EVENT)
                        .set(AE_TYPE, e.getType())
                        .set(AE_SYMBOL, e.getSymbol())
                        .set(AE_BUY_EXCHANGE, e.getBuyExchange())
                        .set(AE_SELL_EXCHANGE, e.getSellExchange())
                        .set(AE_BUY_PRICE, e.getBuyPrice())
                        .set(AE_SELL_PRICE, e.getSellPrice())
                        .set(AE_SPREAD_PERCENT, e.getSpreadPercent())
                        .set(AE_NET_PROFIT, e.getNetProfit())
                        .set(AE_MAX_VOLUME, e.getMaxVolume())
                        .set(AE_SLIPPAGE, e.getSlippage())
                        .set(AE_DETECTED_AT, e.getDetectedAt()))
                .toList();

        dsl.batch(inserts).execute();
    }

    public void deleteByDetectedAtBefore(Instant threshold) {
        dsl.deleteFrom(ARBITRAGE_EVENT)
                .where(AE_DETECTED_AT.lt(threshold))
                .execute();
    }
}
