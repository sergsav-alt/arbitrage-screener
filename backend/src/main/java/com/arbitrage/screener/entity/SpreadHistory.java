package com.arbitrage.screener.entity;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class SpreadHistory {

    Long id;
    String symbol;
    String buyExchange;
    String sellExchange;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    BigDecimal spreadPercent;
    BigDecimal netProfit;
    BigDecimal maxVolume;
    Instant recordedAt;
}
