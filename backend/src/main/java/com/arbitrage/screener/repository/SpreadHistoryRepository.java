package com.arbitrage.screener.repository;

import com.arbitrage.screener.entity.SpreadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SpreadHistoryRepository extends JpaRepository<SpreadHistory, Long> {

    List<SpreadHistory> findBySymbolAndRecordedAtBetween(String symbol, Instant from, Instant to);

    List<SpreadHistory> findByRecordedAtBetween(Instant from, Instant to);

    void deleteByRecordedAtBefore(Instant threshold);
}
