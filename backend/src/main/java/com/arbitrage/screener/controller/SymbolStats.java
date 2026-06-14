package com.arbitrage.screener.controller;

public record SymbolStats(
        String symbol,
        double averageSpread,
        double maxSpread,
        long count
) {}
