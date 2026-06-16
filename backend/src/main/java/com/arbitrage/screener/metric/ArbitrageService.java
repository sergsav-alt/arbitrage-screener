package com.arbitrage.screener.metric;

import com.arbitrage.screener.model.ArbitrageOpportunity;
import com.arbitrage.screener.model.TickerCache;
import com.arbitrage.screener.exchange.PriceNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Сервис арбитража — оркестрирует процесс обнаружения возможностей:
 * получает свежие тикеры, группирует по символам, находит возможности,
 * сохраняет в репозиторий.
 */
@Slf4j
@Service
public class ArbitrageService {

    private final TickerCache tickerCache;
    private final PriceNormalizer priceNormalizer;
    private final ArbitrageDetector detector;
    private final OpportunityRepository repository;
    private final List<Consumer<Collection<ArbitrageOpportunity>>> scanListeners;

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
        this.scanListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Регистрирует слушатель, вызываемый после каждого сканирования.
     * Передаёт список всех текущих возможностей из репозитория.
     */
    public void onScanComplete(Consumer<Collection<ArbitrageOpportunity>> listener) {
        scanListeners.add(listener);
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

        if (!opportunities.isEmpty()) {
            notifyListeners();
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

    private void notifyListeners() {
        var all = repository.getAll();
        scanListeners.forEach(listener -> listener.accept(all));
    }
}
