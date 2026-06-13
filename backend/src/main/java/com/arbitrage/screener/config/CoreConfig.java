package com.arbitrage.screener.config;

import com.arbitrage.screener.entity.TickerCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public TickerCache tickerCache() {
        return new TickerCache();
    }
}
