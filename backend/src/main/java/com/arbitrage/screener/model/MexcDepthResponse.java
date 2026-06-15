package com.arbitrage.screener.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MexcDepthResponse(
        boolean success,
        int code,
        MexcDepthData data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MexcDepthData(
            List<String[]> bids,
            List<String[]> asks,
            long timestamp
    ) {}
}
