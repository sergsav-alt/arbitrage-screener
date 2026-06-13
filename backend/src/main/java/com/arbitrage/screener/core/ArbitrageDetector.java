package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.ArbitrageOpportunity;
import com.arbitrage.screener.entity.ArbitrageType;
import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.exchange.ExchangeEnum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Детектор арбитражных возможностей.
 * Сравнивает тикеры с разных бирж для каждой торговой пары
 * и находит кроссбиржевые арбитражные возможности.
 */
public class ArbitrageDetector {

    private final SpreadCalculator spreadCalculator;

    public ArbitrageDetector(SpreadCalculator spreadCalculator) {
        this.spreadCalculator = spreadCalculator;
    }

    /**
     * Находит все арбитражные возможности среди сгруппированных тикеров.
     *
     * @param groupedTickers тикеры, сгруппированные по символу (BTCUSDT → {BINANCE: ticker, BINGX: ticker})
     * @return список найденных возможностей (включая убыточные)
     */
    public List<ArbitrageOpportunity> detect(Map<String, Map<ExchangeEnum, Ticker>> groupedTickers) {
        var opportunities = new ArrayList<ArbitrageOpportunity>();

        for (var entry : groupedTickers.entrySet()) {
            var symbol = entry.getKey();
            var tickersByExchange = entry.getValue();

            if (tickersByExchange.size() < 2) {
                continue; // нужны минимум 2 биржи для кроссбиржевого арбитража
            }

            var exchanges = new ArrayList<>(tickersByExchange.keySet());

            for (int i = 0; i < exchanges.size(); i++) {
                for (int j = i + 1; j < exchanges.size(); j++) {
                    var exchangeA = exchanges.get(i);
                    var exchangeB = exchanges.get(j);
                    var tickerA = tickersByExchange.get(exchangeA);
                    var tickerB = tickersByExchange.get(exchangeB);

                    // Проверяем оба направления: A→B и B→A
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
        var spreadPercent = spreadCalculator.calculateSpreadPercent(buyTicker, sellTicker);
        var absoluteSpread = spreadCalculator.calculateAbsoluteSpread(buyTicker, sellTicker);
        var maxVolume = spreadCalculator.calculateMaxVolume(buyTicker, sellTicker);

        var opportunity = new ArbitrageOpportunity(
                buildId(symbol, buyTicker.exchange(), sellTicker.exchange()),
                symbol,
                buyTicker.exchange(),
                sellTicker.exchange(),
                buyTicker.askPrice(),
                sellTicker.bidPrice(),
                spreadPercent,
                absoluteSpread,           // netProfit = absoluteSpread пока (без учёта комиссий)
                maxVolume,
                BigDecimal.ZERO,           // slippage — будет реализован в Sprint 5
                ArbitrageType.CROSS_EXCHANGE,
                Instant.now()
        );

        opportunities.add(opportunity);
    }

    private static String buildId(String symbol, ExchangeEnum buyExchange, ExchangeEnum sellExchange) {
        return symbol + "-" + buyExchange.name() + "-" + sellExchange.name();
    }
}
