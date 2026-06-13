package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.Ticker;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Калькулятор кроссбиржевого спреда.
 * Рассчитывает спред между ценой покупки на одной бирже и ценой продажи на другой.
 */
public class SpreadCalculator {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Рассчитывает спред в процентах.
     * Формула: (sellBidPrice - buyAskPrice) / buyAskPrice * 100.
     *
     * @param buyTicker  тикер биржи, где покупаем (используется askPrice)
     * @param sellTicker тикер биржи, где продаём (используется bidPrice)
     * @return спред в процентах (может быть отрицательным, если арбитраж убыточен)
     */
    public BigDecimal calculateSpreadPercent(Ticker buyTicker, Ticker sellTicker) {
        var buyPrice = buyTicker.askPrice();
        var sellPrice = sellTicker.bidPrice();

        if (buyPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return sellPrice.subtract(buyPrice)
                .divide(buyPrice, MC)
                .multiply(ONE_HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает абсолютный спред (разницу цен).
     */
    public BigDecimal calculateAbsoluteSpread(Ticker buyTicker, Ticker sellTicker) {
        return sellTicker.bidPrice().subtract(buyTicker.askPrice());
    }

    /**
     * Определяет максимальный объём, доступный для арбитражной сделки.
     * Ограничен минимальным из объёмов bid и ask.
     */
    public BigDecimal calculateMaxVolume(Ticker buyTicker, Ticker sellTicker) {
        var buyVolume = buyTicker.askQty();
        var sellVolume = sellTicker.bidQty();
        return buyVolume.min(sellVolume);
    }

    /**
     * Проверяет, есть ли положительный спред (потенциальная прибыль).
     */
    public boolean isProfitable(Ticker buyTicker, Ticker sellTicker) {
        return calculateSpreadPercent(buyTicker, sellTicker).signum() > 0;
    }
}
