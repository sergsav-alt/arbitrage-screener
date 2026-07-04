package com.arbitrage.screener.exchange;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ExchangeEnum {
    BINGX("BingX", "wss://open-api-swap.bingx.com/swap-market"),
    MEXC("MEXC", "wss://contract.mexc.com/edge"),
    BINANCE("Binance", "wss://fstream.binance.com");

    private final String displayName;
    private final String wsUrl;

    ExchangeEnum(String displayName, String wsUrl) {
        this.displayName = displayName;
        this.wsUrl = wsUrl;
    }

    /**
     * Разбирает название биржи (регистронезависимо) в {@link ExchangeEnum}.
     *
     * @throws IllegalArgumentException если биржа не поддерживается
     */
    public static ExchangeEnum fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Invalid exchange: " + value + ". Available: " + Arrays.toString(values()));
        }
    }
}
