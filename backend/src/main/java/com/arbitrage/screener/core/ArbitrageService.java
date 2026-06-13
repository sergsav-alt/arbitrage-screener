package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.ArbitrageOpportunity;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.PriceNormalizer;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Сервис арбитража — оркестрирует процесс обнаружения возможностей:
 * получает свежие тикеры, группирует по символам, находит возможности,
 * сохраняет в репозиторий.
 */
@Slf4j
public class ArbitrageService {

    private final TickerCache tickerCache;
    private final PriceNormalizer priceNormalizer;
    private final ArbitrageDetector detector;
    private final OpportunityRepository repository;

    public ArbitrageService(
            TickerCache tickerCache,
            PriceNormalizer priceNormalizer,
            ArbitrageDetector detector,
            OpportunityRepository repository
    ) {
        this.tickerCache = tickerCache;
        this.priceNormalizer = priceNormalizer;
        this.detector = detector;
        this.repository = repository;
    }

    /**
     * Запускает полный цикл сканирования:
     * 1. Очищает устаревшие тикеры из кэша
     * 2. Группирует свежие тикеры по символам
     * 3. Находит арбитражные возможности
     * 4. Сохраняет результаты в репозиторий
     *
     * @return количество найденных возможностей
     */
    public int scan() {
        tickerCache.evictExpired();

        var freshTickers = tickerCache.getFresh();
        if (freshTickers.isEmpty()) {
            log.debug("No fresh tickers to scan");
            return 0;
        }

        var grouped = priceNormalizer.groupBySymbol();
        if (grouped.isEmpty()) {
            return 0;
        }

        var opportunities = detector.detect(grouped);

        for (var opportunity : opportunities) {
            repository.save(opportunity);
        }

        log.debug("Scan complete: {} tickers, {} symbols, {} opportunities",
                freshTickers.size(), grouped.size(), opportunities.size());

        return opportunities.size();
    }

    /**
     * Возвращает все текущие арбитражные возможности.
     */
    public Collection<ArbitrageOpportunity> getOpportunities() {
        return repository.getAll();
    }

    /**
     * Возвращает только прибыльные возможности (spread > 0).
     */
    public Collection<ArbitrageOpportunity> getProfitableOpportunities() {
        return repository.getProfitable();
    }

    /**
     * Возвращает возможности для конкретного символа.
     */
    public Collection<ArbitrageOpportunity> getOpportunitiesBySymbol(String symbol) {
        return repository.getBySymbol(symbol);
    }

    /**
     * Возвращает количество найденных возможностей.
     */
    public int getOpportunityCount() {
        return repository.size();
    }
}
