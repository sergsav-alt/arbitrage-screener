package com.arbitrage.screener.config;

import com.arbitrage.screener.core.*;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.PriceNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class CoreConfig {

    @Value("${arbitrage.default-trade-volume-usdt:100}")
    private BigDecimal defaultTradeVolumeUsdt;

    @Value("${arbitrage.min-trade-volume-usdt:10}")
    private BigDecimal minTradeVolumeUsdt;

    @Bean
    public TickerCache tickerCache() {
        return new TickerCache();
    }

    @Bean
    public OrderBookCache orderBookCache() {
        return new OrderBookCache();
    }

    @Bean
    public ExchangeAdapterFactory exchangeAdapterFactory() {
        return new ExchangeAdapterFactory();
    }

    @Bean
    public PriceNormalizer priceNormalizer(TickerCache tickerCache) {
        return new PriceNormalizer(tickerCache);
    }

    @Bean
    public SpreadCalculator spreadCalculator() {
        return new SpreadCalculator();
    }

    @Bean
    public FeeCalculator feeCalculator(FeeConfig feeConfig) {
        return new FeeCalculator(feeConfig);
    }

    @Bean
    public WithdrawalFeeCalculator withdrawalFeeCalculator(FeeConfig feeConfig) {
        return new WithdrawalFeeCalculator(feeConfig);
    }

    @Bean
    public LiquidityChecker liquidityChecker(OrderBookCache orderBookCache) {
        return new LiquidityChecker(orderBookCache);
    }

    @Bean
    public SlippageCalculator slippageCalculator() {
        return new SlippageCalculator();
    }

    @Bean
    public VolumeLimiter volumeLimiter() {
        return new VolumeLimiter(minTradeVolumeUsdt);
    }

    @Bean
    public OpportunityRepository opportunityRepository() {
        return new OpportunityRepository();
    }

    @Bean
    public ArbitrageDetector arbitrageDetector(
            SpreadCalculator spreadCalculator,
            FeeCalculator feeCalculator,
            WithdrawalFeeCalculator withdrawalFeeCalculator,
            LiquidityChecker liquidityChecker,
            SlippageCalculator slippageCalculator,
            VolumeLimiter volumeLimiter,
            OrderBookCache orderBookCache
    ) {
        return new ArbitrageDetector(
                spreadCalculator,
                feeCalculator,
                withdrawalFeeCalculator,
                liquidityChecker,
                slippageCalculator,
                volumeLimiter,
                orderBookCache,
                defaultTradeVolumeUsdt
        );
    }

    @Bean
    public ArbitrageService arbitrageService(
            TickerCache tickerCache,
            PriceNormalizer priceNormalizer,
            ArbitrageDetector arbitrageDetector,
            OpportunityRepository opportunityRepository
    ) {
        return new ArbitrageService(tickerCache, priceNormalizer, arbitrageDetector, opportunityRepository);
    }

    @Bean
    public ExchangeManager exchangeManager(
            ExchangeAdapterFactory exchangeAdapterFactory,
            TickerCache tickerCache,
            OrderBookCache orderBookCache
    ) {
        return new ExchangeManager(exchangeAdapterFactory, tickerCache, orderBookCache);
    }
}
