package com.arbitrage.screener.controller;

public record AggregatedStatistics(
        double averageSpread,
        double maxSpread,
        long opportunityCount,
        java.util.List<SymbolStats> topSymbols
) {}
