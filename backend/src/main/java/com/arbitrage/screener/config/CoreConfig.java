package com.arbitrage.screener.config;

import com.arbitrage.screener.core.*;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.PriceNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public TickerCache tickerCache() {
        return new TickerCache();
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
    public OpportunityRepository opportunityRepository() {
        return new OpportunityRepository();
    }

    @Bean
    public ArbitrageDetector arbitrageDetector(SpreadCalculator spreadCalculator) {
        return new ArbitrageDetector(spreadCalculator);
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
            TickerCache tickerCache
    ) {
        return new ExchangeManager(exchangeAdapterFactory, tickerCache);
    }
}
