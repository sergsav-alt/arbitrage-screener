package com.arbitrage.screener.controller;

import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.binance.BinanceAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Контроллер для тестирования WebSocket-подключения к биржам.
 * Позволяет подключиться, отключиться, просмотреть статус и получать тикеры в реальном времени.
 */
@Slf4j
@RestController
@RequestMapping("/api/ws")
public class WebSocketTestController {

    private final TickerCache tickerCache;
    private final List<SseEmitter> emitters;
    private volatile BinanceAdapter binanceAdapter;

    public WebSocketTestController(TickerCache tickerCache) {
        this.tickerCache = tickerCache;
        this.emitters = new CopyOnWriteArrayList<>();
    }

    @PostMapping("/binance/connect")
    public Map<String, Object> connect(@RequestParam(defaultValue = "btcusdt,ethusdt") List<String> symbols) {
        if (binanceAdapter != null && binanceAdapter.isConnected()) {
            return Map.of("status", "already_connected", "message", "Already connected to Binance");
        }

        binanceAdapter = new BinanceAdapter();
        var streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@miniTicker")
                .toArray(String[]::new);
        binanceAdapter.subscribe(streams);

        binanceAdapter.onTicker((Consumer<Ticker>) ticker -> {
            tickerCache.put(ticker);
            notifyEmitters(ticker);
        });

        binanceAdapter.connect();

        return Map.of(
                "status", "connecting",
                "symbols", symbols,
                "streams", streams
        );
    }

    @PostMapping("/binance/disconnect")
    public Map<String, Object> disconnect() {
        if (binanceAdapter == null) {
            return Map.of("status", "not_connected", "message", "No active connection");
        }
        binanceAdapter.disconnect();
        binanceAdapter = null;
        return Map.of("status", "disconnected");
    }

    @GetMapping("/binance/status")
    public Map<String, Object> status() {
        var connected = binanceAdapter != null && binanceAdapter.isConnected();
        return Map.of(
                "connected", connected,
                "tickers_in_cache", tickerCache.size(),
                "last_update", tickerCache.getLastUpdateTime()
        );
    }

    @GetMapping("/binance/tickers")
    public Collection<Ticker> tickers() {
        return tickerCache.getAll();
    }

    @GetMapping("/binance/stream")
    public SseEmitter stream() {
        var emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    private void notifyEmitters(Ticker ticker) {
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ticker")
                        .data(ticker, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("SSE emitter removed due to error: {}", e.getMessage());
            }
        }
    }
}
