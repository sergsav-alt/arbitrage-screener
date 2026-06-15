package com.arbitrage.screener.model;

import com.arbitrage.screener.exchange.ExchangeEnum;

import java.util.List;

public record OrderBook(
        ExchangeEnum exchange,
        String symbol,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Long timestamp
) {}
