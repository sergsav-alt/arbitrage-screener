package com.arbitrage.screener.repository;

import com.arbitrage.screener.entity.SpreadHistory;

import java.time.Instant;
import java.util.List;

public interface SpreadHistoryRepository {

    void saveAll(List<SpreadHistory> histories);

    List<SpreadHistory> findBySymbolAndRecordedAtBetween(String symbol, Instant from, Instant to);

    List<SpreadHistory> findByRecordedAtBetween(Instant from, Instant to);

    void deleteByRecordedAtBefore(Instant threshold);
}
