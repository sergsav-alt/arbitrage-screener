package com.arbitrage.screener.repository;

import com.arbitrage.screener.entity.ArbitrageEvent;

import java.time.Instant;
import java.util.List;

public interface ArbitrageEventRepository {

    void saveAll(List<ArbitrageEvent> events);

    void deleteByDetectedAtBefore(Instant threshold);
}
