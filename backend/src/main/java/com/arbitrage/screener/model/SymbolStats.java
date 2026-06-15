package com.arbitrage.screener.model;

public record SymbolStats(
        String symbol,
        double averageSpread,
        double maxSpread,
        long count
) {}
