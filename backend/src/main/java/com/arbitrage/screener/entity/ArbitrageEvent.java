package com.arbitrage.screener.entity;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class ArbitrageEvent {

    Long id;
    String type;
    String symbol;
    String buyExchange;
    String sellExchange;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    BigDecimal spreadPercent;
    BigDecimal netProfit;
    BigDecimal maxVolume;
    BigDecimal slippage;
    Instant detectedAt;
}
