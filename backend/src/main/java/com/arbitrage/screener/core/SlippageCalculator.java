package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.OrderBook;
import com.arbitrage.screener.entity.OrderBookLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Оценивает проскальзывание (slippage) при рыночной покупке/продаже заданного объёма.
 * Проходит по уровням стакана и вычисляет средневзвешенную цену исполнения.
 */
public class SlippageCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Оценивает проскальзывание в USDT при покупке заданного объёма базового актива.
     * Использует уровни ASK (продавцы).
     *
     * @param orderBook стакан заявок
     * @param volume    объём базового актива для покупки
     * @return проскальзывание в USDT (разница между avgPrice и bestPrice, умноженная на volume)
     */
    public BigDecimal calculateBuySlippage(OrderBook orderBook, BigDecimal volume) {
        return calculateSlippage(orderBook.asks(), volume, true);
    }

    /**
     * Оценивает проскальзывание в USDT при продаже заданного объёма базового актива.
     * Использует уровни BID (покупатели).
     *
     * @param orderBook стакан заявок
     * @param volume    объём базового актива для продажи
     * @return проскальзывание в USDT
     */
    public BigDecimal calculateSellSlippage(OrderBook orderBook, BigDecimal volume) {
        return calculateSlippage(orderBook.bids(), volume, false);
    }

    /**
     * Вычисляет проскальзывание при исполнении заданного объёма.
     * Проходит по уровням стакана до исчерпания объёма и считает средневзвешенную цену.
     *
     * @param levels  уровни стакана (asks или bids)
     * @param volume  объём для исполнения
     * @param isAsk   true — ASK (покупка), false — BID (продажа)
     * @return slippage в USDT
     */
    private BigDecimal calculateSlippage(List<OrderBookLevel> levels, BigDecimal volume, boolean isAsk) {
        if (levels.isEmpty() || volume.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        var bestPrice = levels.get(0).price();
        var remaining = volume;
        var totalCost = BigDecimal.ZERO;

        for (var level : levels) {
            var levelVolume = level.quantity().min(remaining);
            totalCost = totalCost.add(level.price().multiply(levelVolume));
            remaining = remaining.subtract(levelVolume);
            if (remaining.signum() <= 0) {
                break;
            }
        }

        if (remaining.signum() > 0) {
            // Недостаточно ликвидности — считаем slippage для доступного объёма
            var executedVolume = volume.subtract(remaining);
            if (executedVolume.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return bestPrice.multiply(executedVolume)
                    .subtract(totalCost)
                    .abs()
                    .setScale(8, RoundingMode.HALF_UP);
        }

        var idealCost = bestPrice.multiply(volume);
        return idealCost.subtract(totalCost).abs()
                .setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Оценивает проскальзывание в процентах от объёма сделки в USDT.
     */
    public BigDecimal calculateSlippagePercent(OrderBook orderBook, BigDecimal volume, boolean isAsk) {
        var slippageUsdt = calculateSlippage(
                isAsk ? orderBook.asks() : orderBook.bids(), volume, isAsk);
        var bestPrice = (isAsk ? orderBook.asks() : orderBook.bids()).get(0).price();
        var volumeUsdt = bestPrice.multiply(volume);

        if (volumeUsdt.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return slippageUsdt.divide(volumeUsdt, 8, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
