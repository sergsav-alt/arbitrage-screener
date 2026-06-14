package com.arbitrage.screener.controller;

import com.arbitrage.screener.core.ArbitrageService;
import com.arbitrage.screener.core.ExchangeManager;
import com.arbitrage.screener.model.ArbitrageOpportunity;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArbitrageController {

    @Value("#{'${symbols}'.split(',')}")
    private final List<String> symbols;

    private final ArbitrageService arbitrageService;
    private final ExchangeManager exchangeManager;

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
                .collect(Collectors.toList());
    }

    @GetMapping("/opportunities")
    public Collection<ArbitrageOpportunity> getOpportunities(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String buyExchange,
            @RequestParam(required = false) String sellExchange,
            @RequestParam(required = false, defaultValue = "0") double minSpread
    ) {
        return arbitrageService.getOpportunities().stream()
                .filter(o -> symbol == null || o.symbol().equalsIgnoreCase(symbol))
                .filter(o -> buyExchange == null || o.buyExchange().name().equalsIgnoreCase(buyExchange))
                .filter(o -> sellExchange == null || o.sellExchange().name().equalsIgnoreCase(sellExchange))
                .filter(o -> o.spreadPercent().doubleValue() >= minSpread)
                .collect(Collectors.toList());
    }

    @GetMapping("/opportunities/{id}")
    public ArbitrageOpportunity getOpportunity(@PathVariable String id) {
        return arbitrageService.getOpportunities().stream()
                .filter(o -> o.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Opportunity not found: " + id));
    }

    @GetMapping("/symbols")
    public List<String> getSymbols() {
        return symbols;
    }
}
