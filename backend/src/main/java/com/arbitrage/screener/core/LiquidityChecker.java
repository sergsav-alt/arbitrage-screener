package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.OrderBook;
import com.arbitrage.screener.entity.OrderBookLevel;
import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Проверяет достаточность ликвидности в стакане для сделки заданного объёма.
 */
public class LiquidityChecker {

    private final OrderBookCache orderBookCache;

    public LiquidityChecker(OrderBookCache orderBookCache) {
        this.orderBookCache = orderBookCache;
    }

    /**
     * Проверяет, достаточно ли объёма на стороне ASK стакана
     * для покупки заданного количества базового актива.
     *
     * @param exchange биржа
     * @param symbol   торговая пара
     * @param volume   требуемый объём базового актива
     * @return true, если ликвидности достаточно
     */
    public boolean hasSufficientAskLiquidity(ExchangeEnum exchange, String symbol, BigDecimal volume) {
        return hasSufficientLiquidity(exchange, symbol, volume, true);
    }

    /**
     * Проверяет, достаточно ли объёма на стороне BID стакана
     * для продажи заданного количества базового актива.
     *
     * @param exchange биржа
     * @param symbol   торговая пара
     * @param volume   требуемый объём базового актива
     * @return true, если ликвидности достаточно
     */
    public boolean hasSufficientBidLiquidity(ExchangeEnum exchange, String symbol, BigDecimal volume) {
        return hasSufficientLiquidity(exchange, symbol, volume, false);
    }

    private boolean hasSufficientLiquidity(ExchangeEnum exchange, String symbol,
                                           BigDecimal volume, boolean isAsk) {
        Optional<OrderBook> orderBookOpt = orderBookCache.get(exchange, symbol);
        if (orderBookOpt.isEmpty()) {
            return false;
        }

        var orderBook = orderBookOpt.get();
        var levels = isAsk ? orderBook.asks() : orderBook.bids();
        return getAvailableVolume(levels).compareTo(volume) >= 0;
    }

    /**
     * Возвращает суммарный доступный объём на указанных уровнях стакана.
     */
    public BigDecimal getAvailableVolume(ExchangeEnum exchange, String symbol, boolean isAsk) {
        return orderBookCache.get(exchange, symbol)
                .map(ob -> getAvailableVolume(isAsk ? ob.asks() : ob.bids()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getAvailableVolume(List<OrderBookLevel> levels) {
        return levels.stream()
                .map(OrderBookLevel::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
