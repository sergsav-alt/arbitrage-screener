package com.arbitrage.screener.model;

import java.math.BigDecimal;
import java.time.Instant;

public record SpreadHistoryPoint(
        Instant recordedAt,
        BigDecimal spreadPercent,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        BigDecimal netProfit
) {}
