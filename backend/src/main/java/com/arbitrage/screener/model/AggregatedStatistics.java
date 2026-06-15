package com.arbitrage.screener.model;

public record AggregatedStatistics(
        double averageSpread,
        double maxSpread,
        long opportunityCount,
        java.util.List<SymbolStats> topSymbols
) {}
