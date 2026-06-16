package com.arbitrage.screener.metric;

import com.arbitrage.screener.entity.ArbitrageEvent;
import com.arbitrage.screener.entity.SpreadHistory;
import com.arbitrage.screener.model.ArbitrageOpportunity;
import com.arbitrage.screener.repository.ArbitrageEventRepository;
import com.arbitrage.screener.repository.SpreadHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsCollector {

    private final SpreadHistoryRepository spreadHistoryRepository;
    private final ArbitrageEventRepository arbitrageEventRepository;
    private final ArbitrageService arbitrageService;

    @Value("${statistics.collect-interval-ms:60000}")
    private long collectIntervalMs;

    @PostConstruct
    public void init() {
        arbitrageService.onScanComplete(this::onScanComplete);
        log.info("StatisticsCollector initialized, collect interval: {} ms", collectIntervalMs);
    }

    @Scheduled(fixedDelayString = "${statistics.collect-interval-ms:60000}")
    @Transactional
    public void collectSnapshot() {
        var opportunities = arbitrageService.getProfitableOpportunities();
        if (opportunities.isEmpty()) {
            return;
        }

        var now = Instant.now();
        var histories = opportunities.stream()
                .map(o -> toSpreadHistory(o, now))
                .collect(Collectors.toList());

        spreadHistoryRepository.saveAll(histories);
        log.debug("Saved {} spread history snapshots", histories.size());
    }

    private void onScanComplete(Collection<ArbitrageOpportunity> opportunities) {
        var now = Instant.now();
        var events = opportunities.stream()
                .filter(o -> o.spreadPercent().signum() > 0)
                .map(o -> toArbitrageEvent(o, now))
                .collect(Collectors.toList());

        if (!events.isEmpty()) {
            arbitrageEventRepository.saveAll(events);
            log.debug("Saved {} arbitrage events", events.size());
        }
    }

    private SpreadHistory toSpreadHistory(ArbitrageOpportunity o, Instant recordedAt) {
        return SpreadHistory.builder()
                .symbol(o.symbol())
                .buyExchange(o.buyExchange().name())
                .sellExchange(o.sellExchange().name())
                .buyPrice(o.buyPrice())
                .sellPrice(o.sellPrice())
                .spreadPercent(o.spreadPercent())
                .netProfit(o.netProfit())
                .maxVolume(o.maxVolume())
                .recordedAt(recordedAt)
                .build();
    }

    private ArbitrageEvent toArbitrageEvent(ArbitrageOpportunity o, Instant detectedAt) {
        return ArbitrageEvent.builder()
                .type(o.type().name())
                .symbol(o.symbol())
                .buyExchange(o.buyExchange().name())
                .sellExchange(o.sellExchange().name())
                .buyPrice(o.buyPrice())
                .sellPrice(o.sellPrice())
                .spreadPercent(o.spreadPercent())
                .netProfit(o.netProfit())
                .maxVolume(o.maxVolume())
                .slippage(o.slippage())
                .detectedAt(detectedAt)
                .build();
    }
}
