package com.arbitrage.screener.repository;

import com.arbitrage.screener.entity.ArbitrageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ArbitrageEventRepository extends JpaRepository<ArbitrageEvent, Long> {

    void deleteByDetectedAtBefore(Instant threshold);
}
