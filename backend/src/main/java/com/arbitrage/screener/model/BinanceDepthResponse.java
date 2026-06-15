package com.arbitrage.screener.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceDepthResponse(
        long lastUpdateId,
        @JsonProperty("E") long eventTime,
        @JsonProperty("T") long transactionTime,
        List<String[]> bids,
        List<String[]> asks
) {}
