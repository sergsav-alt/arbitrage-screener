package com.arbitrage.screener.model;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record ArbitrageOpportunity(
        String id,
        String symbol,
        ExchangeEnum buyExchange,
        ExchangeEnum sellExchange,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        BigDecimal spreadPercent,
        BigDecimal netProfit,
        BigDecimal maxVolume,
        BigDecimal slippage,
        ArbitrageType type,
        Instant detectedAt
) {}
