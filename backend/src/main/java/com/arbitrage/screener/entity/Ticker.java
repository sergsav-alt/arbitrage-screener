package com.arbitrage.screener.entity;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record Ticker(
        ExchangeEnum exchange,
        String symbol,
        BigDecimal bidPrice,
        BigDecimal bidQty,
        BigDecimal askPrice,
        BigDecimal askQty,
        Instant timestamp
) {}
