package com.arbitrage.screener.config;

import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeManager;
import com.arbitrage.screener.cache.OrderBookCache;
import com.arbitrage.screener.cache.DealCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public ExchangeManager exchangeManager(
            ExchangeAdapterFactory exchangeAdapterFactory,
            DealCache dealCache,
            OrderBookCache orderBookCache
    ) {
        return new ExchangeManager(exchangeAdapterFactory, dealCache, orderBookCache);
    }
}
