package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Контроллер метаданных рынка: статусы подключения бирж и список доступных символов.
 */
@RestController
@RequestMapping("/api")
public class MarketDataController {

    private final List<String> symbols;
    private final ExchangeManager exchangeManager;

    public MarketDataController(
            @Value("#{'${symbols}'.split(',')}") List<String> symbols,
            ExchangeManager exchangeManager) {
        this.symbols = symbols;
        this.exchangeManager = exchangeManager;
    }

    @GetMapping("/exchanges")
    public List<Map<String, Object>> getExchanges() {
        var statusMap = exchangeManager.getStatus();

        return Stream.of(ExchangeEnum.values())
                .map(exchange -> {
                    var status = new LinkedHashMap<String, Object>();
                    status.put("exchange", exchange.name());
                    status.put("displayName", exchange.getDisplayName());
                    status.put("connected", statusMap.getOrDefault(exchange, false));
                    return (Map<String, Object>) status;
                })
                .toList();
    }

    @GetMapping("/symbols")
    public List<String> getSymbols() {
        return symbols.stream()
                .map(symbol -> new StringBuilder(symbol).insert(3, "-").toString())
                .toList();
    }
}
