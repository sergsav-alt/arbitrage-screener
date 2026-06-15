package com.arbitrage.screener.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BingXDepthResponse(
        int code,
        String msg,
        BingXDepthData data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BingXDepthData(
            @JsonProperty("T") long timestamp,
            List<String[]> bids,
            List<String[]> asks
    ) {}
}
