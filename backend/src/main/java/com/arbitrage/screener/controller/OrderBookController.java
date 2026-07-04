package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.depth.DepthSnapshotProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Единый контроллер snapshot стакана ордеров для всех бирж.
 * Делегирует получение данных бирже-специфичной стратегии {@link DepthSnapshotProvider}.
 * Эндпоинт: GET /api/{exchange}/ob?symbol=BTC-USDT&depth=20
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class OrderBookController {

    private final Map<ExchangeEnum, DepthSnapshotProvider> providers;

    public OrderBookController(List<DepthSnapshotProvider> providers) {
        this.providers = new EnumMap<>(ExchangeEnum.class);
        providers.forEach(provider -> this.providers.put(provider.exchange(), provider));
    }

    @GetMapping("/{exchange}/ob")
    public ResponseEntity<Object> getDepth(
            @PathVariable String exchange,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int depth
    ) {
        var exchangeEnum = ExchangeEnum.fromString(exchange);
        var provider = providers.get(exchangeEnum);
        if (provider == null) {
            throw new IllegalArgumentException("No depth provider for exchange: " + exchangeEnum);
        }

        log.info("Fetching {} depth: symbol={}, limit={}", exchangeEnum, symbol, depth);
        return ResponseEntity.ok(provider.fetchSnapshot(symbol, depth));
    }
}
