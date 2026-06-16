package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/api/stream/bingx")
public class BingXSseController {

    private final List<SseEmitter> emitters;

    public BingXSseController(ExchangeAdapterFactory adapterFactory) {
        this.emitters = new CopyOnWriteArrayList<>();
        var adapter = adapterFactory.getAdapter(ExchangeEnum.BINGX);

        adapter.onTicker(ticker -> broadcast("ticker", ticker));
        adapter.onOrderBook(orderBook -> broadcast("orderbook", orderBook));

        log.info("BingXSseController initialized and subscribed to BingXAdapter");
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        var emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("BingX SSE client disconnected (completed), remaining: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("BingX SSE client disconnected (timeout), remaining: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("BingX SSE client disconnected (error): {}", e.getMessage());
        });

        log.info("BingX SSE client connected, total: {}", emitters.size());
        return emitter;
    }

    private void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) {
            return;
        }
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("BingX SSE emitter removed due to error: {}", e.getMessage());
            }
        }
    }
}
