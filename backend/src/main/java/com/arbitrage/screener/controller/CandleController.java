package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.candle.CandleProvider;
import com.arbitrage.screener.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер свечных данных (klines).
 * <ul>
 *   <li>{@code GET /api/candles/{exchange}/history} — история свечей через REST;</li>
 *   <li>{@code GET /api/candles/{exchange}/stream}  — live-обновления свечей по SSE.</li>
 * </ul>
 * Делегирует получение данных бирже-специфичной стратегии {@link CandleProvider}.
 */
@Slf4j
@RestController
@RequestMapping("/api/candles")
public class CandleController {

    private final Map<ExchangeEnum, CandleProvider> providers;

    public CandleController(List<CandleProvider> providers) {
        this.providers = new EnumMap<>(ExchangeEnum.class);
        providers.forEach(provider -> this.providers.put(provider.exchange(), provider));
    }

    @GetMapping("/{exchange}/history")
    public List<Candle> history(
            @PathVariable String exchange,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "500") int limit
    ) {
        var provider = resolveProvider(exchange);
        log.info("Fetching {} candles: symbol={}, interval={}, limit={}", exchange, symbol, interval, limit);
        return provider.fetchHistory(symbol, interval, limit);
    }

    @GetMapping(value = "/{exchange}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String exchange,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        var provider = resolveProvider(exchange);
        var emitter = new SseEmitter(0L);

        AutoCloseable subscription = provider.stream(
                symbol,
                interval,
                candle -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("candle")
                                .data(candle, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        log.debug("{} candle SSE send failed: {}", exchange, e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.warn("{} candle stream error: {}", exchange, error.getMessage());
                    emitter.completeWithError(error);
                });

        Runnable cleanup = () -> {
            try {
                subscription.close();
            } catch (Exception e) {
                log.debug("{} candle subscription close failed: {}", exchange, e.getMessage());
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.info("Candle SSE client connected: exchange={}, symbol={}, interval={}", exchange, symbol, interval);
        return emitter;
    }

    private CandleProvider resolveProvider(String exchange) {
        var exchangeEnum = ExchangeEnum.fromString(exchange);
        var provider = providers.get(exchangeEnum);
        if (provider == null) {
            throw new IllegalArgumentException("Candles are not supported for exchange: " + exchangeEnum);
        }
        return provider;
    }
}
