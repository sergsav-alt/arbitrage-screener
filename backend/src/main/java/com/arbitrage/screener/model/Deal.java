package com.arbitrage.screener.model;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;

public record Deal(
        ExchangeEnum exchange,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        Long timestamp
) {}