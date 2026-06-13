package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.ArbitrageOpportunity;
import com.arbitrage.screener.entity.ArbitrageType;
import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Детектор арбитражных возможностей с учётом комиссий, ликвидности и проскальзывания.
 * Сравнивает тикеры с разных бирж для каждой торговой пары
 * и находит кроссбиржевые арбитражные возможности с реалистичным расчётом чистой прибыли.
 */
@Slf4j
public class ArbitrageDetector {

    private final SpreadCalculator spreadCalculator;
    private final FeeCalculator feeCalculator;
    private final WithdrawalFeeCalculator withdrawalFeeCalculator;
    private final LiquidityChecker liquidityChecker;
    private final SlippageCalculator slippageCalculator;
    private final VolumeLimiter volumeLimiter;
    private final OrderBookCache orderBookCache;
    private final BigDecimal defaultTradeVolumeUsdt;

    public ArbitrageDetector(SpreadCalculator spreadCalculator,
                             FeeCalculator feeCalculator,
                             WithdrawalFeeCalculator withdrawalFeeCalculator,
                             LiquidityChecker liquidityChecker,
                             SlippageCalculator slippageCalculator,
                             VolumeLimiter volumeLimiter,
                             OrderBookCache orderBookCache,
                             BigDecimal defaultTradeVolumeUsdt) {
        this.spreadCalculator = spreadCalculator;
        this.feeCalculator = feeCalculator;
        this.withdrawalFeeCalculator = withdrawalFeeCalculator;
        this.liquidityChecker = liquidityChecker;
        this.slippageCalculator = slippageCalculator;
        this.volumeLimiter = volumeLimiter;
        this.orderBookCache = orderBookCache;
        this.defaultTradeVolumeUsdt = defaultTradeVolumeUsdt;
    }

    /**
     * Находит все арбитражные возможности среди сгруппированных тикеров.
     */
    public List<ArbitrageOpportunity> detect(Map<String, Map<ExchangeEnum, Ticker>> groupedTickers) {
        var opportunities = new ArrayList<ArbitrageOpportunity>();

        for (var entry : groupedTickers.entrySet()) {
            var symbol = entry.getKey();
            var tickersByExchange = entry.getValue();

            if (tickersByExchange.size() < 2) {
                continue;
            }

            var exchanges = new ArrayList<>(tickersByExchange.keySet());

            for (int i = 0; i < exchanges.size(); i++) {
                for (int j = i + 1; j < exchanges.size(); j++) {
                    var exchangeA = exchanges.get(i);
                    var exchangeB = exchanges.get(j);
                    var tickerA = tickersByExchange.get(exchangeA);
                    var tickerB = tickersByExchange.get(exchangeB);

                    detectDirection(opportunities, symbol, tickerA, tickerB);
                    detectDirection(opportunities, symbol, tickerB, tickerA);
                }
            }
        }

        return opportunities;
    }

    /**
     * Проверяет одно направление: покупка на buyExchange, продажа на sellExchange.
     */
    private void detectDirection(
            List<ArbitrageOpportunity> opportunities,
            String symbol,
            Ticker buyTicker,
            Ticker sellTicker
    ) {
        var buyExchange = buyTicker.exchange();
        var sellExchange = sellTicker.exchange();
        var buyPrice = buyTicker.askPrice();
        var sellPrice = sellTicker.bidPrice();

        if (buyPrice.signum() == 0 || sellPrice.signum() == 0) {
            return;
        }

        var spreadPercent = spreadCalculator.calculateSpreadPercent(buyTicker, sellTicker);

        // Расчёт торгового объёма (базовый актив)
        var maxVolumeBase = spreadCalculator.calculateMaxVolume(buyTicker, sellTicker);
        var defaultVolumeBase = defaultTradeVolumeUsdt.divide(buyPrice, 8, RoundingMode.HALF_UP);
        var tradeVolumeBase = maxVolumeBase.min(defaultVolumeBase);

        if (tradeVolumeBase.signum() <= 0) {
            return;
        }

        var tradeVolumeUsdt = tradeVolumeBase.multiply(buyPrice);

        // Проверка minVolume
        if (!volumeLimiter.isAboveMinVolume(tradeVolumeUsdt)) {
            return;
        }

        // Проверка ликвидности
        if (!liquidityChecker.hasSufficientAskLiquidity(buyExchange, symbol, tradeVolumeBase)) {
            return;
        }
        if (!liquidityChecker.hasSufficientBidLiquidity(sellExchange, symbol, tradeVolumeBase)) {
            return;
        }

        // Расчёт комиссий
        var buyFee = feeCalculator.calculateTakerFee(tradeVolumeUsdt, buyExchange);
        var sellFee = feeCalculator.calculateTakerFee(tradeVolumeUsdt, sellExchange);
        var withdrawalFee = withdrawalFeeCalculator.calculate(symbol, sellExchange);

        // Расчёт проскальзывания
        var buySlippage = BigDecimal.ZERO;
        var sellSlippage = BigDecimal.ZERO;

        var buyOrderBook = orderBookCache.get(buyExchange, symbol);
        var sellOrderBook = orderBookCache.get(sellExchange, symbol);

        if (buyOrderBook.isPresent()) {
            buySlippage = slippageCalculator.calculateBuySlippage(buyOrderBook.get(), tradeVolumeBase);
        }
        if (sellOrderBook.isPresent()) {
            sellSlippage = slippageCalculator.calculateSellSlippage(sellOrderBook.get(), tradeVolumeBase);
        }

        var totalSlippage = buySlippage.add(sellSlippage);

        // Расчёт чистой прибыли
        var absoluteSpread = spreadCalculator.calculateAbsoluteSpread(buyTicker, sellTicker);
        var grossProfit = absoluteSpread.multiply(tradeVolumeBase);
        var totalCosts = buyFee.add(sellFee).add(withdrawalFee).add(totalSlippage);
        var netProfit = grossProfit.subtract(totalCosts).setScale(8, RoundingMode.HALF_UP);

        var opportunity = new ArbitrageOpportunity(
                buildId(symbol, buyExchange, sellExchange),
                symbol,
                buyExchange,
                sellExchange,
                buyPrice,
                sellPrice,
                spreadPercent,
                netProfit,
                maxVolumeBase,
                totalSlippage,
                ArbitrageType.CROSS_EXCHANGE,
                Instant.now()
        );

        opportunities.add(opportunity);
    }

    private static String buildId(String symbol, ExchangeEnum buyExchange, ExchangeEnum sellExchange) {
        return symbol + "-" + buyExchange.name() + "-" + sellExchange.name();
    }
}
