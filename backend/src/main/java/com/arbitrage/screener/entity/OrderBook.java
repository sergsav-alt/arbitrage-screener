package com.arbitrage.screener.entity;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.time.Instant;
import java.util.List;

public record OrderBook(
        ExchangeEnum exchange,
        String symbol,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Instant timestamp
) {}
