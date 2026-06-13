package com.arbitrage.screener.core;

import com.arbitrage.screener.config.FeeConfig;
import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Калькулятор taker-комиссии.
 * Рассчитывает комиссию в USDT за сделку на заданный объём.
 */
public class FeeCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final FeeConfig feeConfig;

    public FeeCalculator(FeeConfig feeConfig) {
        this.feeConfig = feeConfig;
    }

    /**
     * Рассчитывает taker-комиссию в USDT.
     *
     * @param volumeUsdt объём сделки в USDT
     * @param exchange   биржа
     * @return комиссия в USDT
     */
    public BigDecimal calculateTakerFee(BigDecimal volumeUsdt, ExchangeEnum exchange) {
        var ratePercent = feeConfig.getTakerFeePercent(exchange);
        if (ratePercent.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return volumeUsdt.multiply(ratePercent)
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }
}
