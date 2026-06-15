package com.arbitrage.screener.controller;

import com.arbitrage.screener.entity.SpreadHistory;
import com.arbitrage.screener.model.AggregatedStatistics;
import com.arbitrage.screener.model.SpreadHistoryPoint;
import com.arbitrage.screener.model.SymbolStats;
import com.arbitrage.screener.repository.SpreadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final SpreadHistoryRepository spreadHistoryRepository;

    /**
     * GET /api/statistics — агрегированная статистика: средний/макс спред,
     * кол-во возможностей, топ-пар за последние 24 часа.
     */
    @GetMapping
    public AggregatedStatistics getAggregatedStatistics() {
        var last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        var records = spreadHistoryRepository.findByRecordedAtBetween(last24h, Instant.now());

        if (records.isEmpty()) {
            return new AggregatedStatistics(0, 0, 0, List.of());
        }

        var avgSpread = records.stream()
                .map(SpreadHistory::getSpreadPercent)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        var maxSpread = records.stream()
                .map(SpreadHistory::getSpreadPercent)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0);

        var topSymbols = records.stream()
                .collect(Collectors.groupingBy(SpreadHistory::getSymbol))
                .entrySet().stream()
                .map(entry -> {
                    var values = entry.getValue();
                    var symbolAvg = values.stream()
                            .map(SpreadHistory::getSpreadPercent)
                            .mapToDouble(BigDecimal::doubleValue)
                            .average()
                            .orElse(0);
                    var symbolMax = values.stream()
                            .map(SpreadHistory::getSpreadPercent)
                            .mapToDouble(BigDecimal::doubleValue)
                            .max()
                            .orElse(0);
                    return new SymbolStats(
                            entry.getKey(),
                            round(symbolAvg),
                            round(symbolMax),
                            values.size()
                    );
                })
                .sorted(Comparator.comparingDouble(SymbolStats::averageSpread).reversed())
                .limit(10)
                .toList();

        return new AggregatedStatistics(
                round(avgSpread),
                round(maxSpread),
                records.size(),
                topSymbols
        );
    }

    /**
     * GET /api/statistics/{symbol} — статистика по конкретной паре за период.
     */
    @GetMapping("/{symbol}")
    public SymbolStats getSymbolStatistics(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        var effectiveTo = to != null ? to : Instant.now();
        var effectiveFrom = from != null ? from : effectiveTo.minus(24, ChronoUnit.HOURS);

        var records = spreadHistoryRepository.findBySymbolAndRecordedAtBetween(
                symbol.toUpperCase(), effectiveFrom, effectiveTo);

        if (records.isEmpty()) {
            return new SymbolStats(symbol.toUpperCase(), 0, 0, 0);
        }

        var avgSpread = records.stream()
                .map(SpreadHistory::getSpreadPercent)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        var maxSpread = records.stream()
                .map(SpreadHistory::getSpreadPercent)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0);

        return new SymbolStats(
                symbol.toUpperCase(),
                round(avgSpread),
                round(maxSpread),
                records.size()
        );
    }

    /**
     * GET /api/statistics/{symbol}/history — временной ряд спредов для графика.
     */
    @GetMapping("/{symbol}/history")
    public List<SpreadHistoryPoint> getSymbolHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        var effectiveTo = to != null ? to : Instant.now();
        var effectiveFrom = from != null ? from : effectiveTo.minus(24, ChronoUnit.HOURS);

        return spreadHistoryRepository.findBySymbolAndRecordedAtBetween(
                symbol.toUpperCase(), effectiveFrom, effectiveTo)
                .stream()
                .map(h -> new SpreadHistoryPoint(
                        h.getRecordedAt(),
                        h.getSpreadPercent(),
                        h.getBuyPrice(),
                        h.getSellPrice(),
                        h.getNetProfit()
                ))
                .sorted(Comparator.comparing(SpreadHistoryPoint::recordedAt))
                .toList();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
