package com.arbitrage.screener.exchange;

import lombok.Getter;

@Getter
public enum ExchangeEnum {
    BINGX("BingX", "wss://open-api-cswap-ws.bingx.com/market"),
    MEXC("MEXC", "wss://contract.mexc.com/edge");

    private final String displayName;
    private final String wsUrl;

    ExchangeEnum(String displayName, String wsUrl) {
        this.displayName = displayName;
        this.wsUrl = wsUrl;
    }
}
