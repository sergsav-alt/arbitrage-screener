package com.arbitrage.screener.core;

import com.arbitrage.screener.exchange.ExchangeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArbitrageScheduler {

    private final ExchangeManager exchangeManager;
    private final ArbitrageService arbitrageService;

    @Value("#{'${symbols}'.split(',')}")
    private final List<String> symbols;

    @PostConstruct
    public void start() {
        var exchanges = Arrays.asList(ExchangeEnum.values());
        log.info("Starting arbitrage scanner with exchanges: {}, symbols: {}", exchanges, symbols);
        exchangeManager.start(exchanges, symbols);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping arbitrage scanner");
        exchangeManager.stop();
    }

    @Scheduled(fixedDelayString = "${arbitrage.scan-interval-ms}")
    public void scan() {
        arbitrageService.scan();
    }
}
