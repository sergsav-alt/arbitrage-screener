package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeManager;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArbitrageController {

    @Value("#{'${symbols}'.split(',')}")
    private final List<String> symbols;

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
                .collect(toList());
    }

    @GetMapping("/symbols")
    public List<String> getSymbols() {
        return symbols.stream()
                .map(symbol -> new StringBuilder(symbol).insert(3, "-").toString())
                .toList();
    }
}
