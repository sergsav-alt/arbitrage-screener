package com.arbitrage.screener.exchange;

public enum ExchangeEnum {
    BINANCE("Binance", "wss://stream.binance.com:9443/ws"),
    BINGX("BingX", "wss://open-api-ws.bingx.com/market"),
    MEXC("MEXC", "wss://wbs.mexc.com/ws");

    private final String displayName;
    private final String wsUrl;

    ExchangeEnum(String displayName, String wsUrl) {
        this.displayName = displayName;
        this.wsUrl = wsUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWsUrl() {
        return wsUrl;
    }
}
