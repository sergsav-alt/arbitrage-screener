package com.arbitrage.screener.config;

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
}
