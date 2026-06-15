package com.arbitrage.screener.model;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;

public record BookTicker(
        ExchangeEnum exchange,
        String symbol,
        BigDecimal askPrice,
        BigDecimal askQty,
        BigDecimal bidPrice,
        BigDecimal bidQty,
        Long timestamp
) {}
