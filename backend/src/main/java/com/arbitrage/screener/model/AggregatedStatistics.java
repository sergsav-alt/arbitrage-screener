package com.arbitrage.screener.model;

import java.util.List;

public record AggregatedStatistics(
        double averageSpread,
        double maxSpread,
        long opportunityCount,
        List<SymbolStats> topSymbols
) {}
