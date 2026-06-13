package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;

/**
 * Калькулятор комиссии на вывод средств.
 * Возвращает фиксированную комиссию за вывод с указанной биржи для заданного символа.
 */
public class WithdrawalFeeCalculator {

    private final FeeConfig feeConfig;

    public WithdrawalFeeCalculator(FeeConfig feeConfig) {
        this.feeConfig = feeConfig;
    }

    /**
     * Возвращает комиссию на вывод в USDT для указанного символа с указанной биржи.
     */
    public BigDecimal calculate(String symbol, ExchangeEnum exchange) {
        return feeConfig.getWithdrawalFee(symbol, exchange);
    }
}
