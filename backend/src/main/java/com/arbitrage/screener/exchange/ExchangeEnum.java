package com.arbitrage.screener.exchange;

import lombok.Getter;

@Getter
public enum ExchangeEnum {
    BINGX("BingX", "wss://open-api-ws.bingx.com/market"),
    MEXC("MEXC", "wss://wbs-api.mexc.com/ws");

    private final String displayName;
    private final String wsUrl;

    ExchangeEnum(String displayName, String wsUrl) {
        this.displayName = displayName;
        this.wsUrl = wsUrl;
    }
}
