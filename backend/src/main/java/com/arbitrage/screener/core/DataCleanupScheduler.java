package com.arbitrage.screener.core;

import com.arbitrage.screener.repository.ArbitrageEventRepository;
import com.arbitrage.screener.repository.SpreadHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private final SpreadHistoryRepository spreadHistoryRepository;
    private final ArbitrageEventRepository arbitrageEventRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldData() {
        var threshold = Instant.now().minus(7, ChronoUnit.DAYS);

        spreadHistoryRepository.deleteByRecordedAtBefore(threshold);
        arbitrageEventRepository.deleteByDetectedAtBefore(threshold);

        log.info("Cleaned up data older than 7 days (threshold: {})", threshold);
    }
}
