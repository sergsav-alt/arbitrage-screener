package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Базовый SSE-контроллер для потоковой передачи данных с биржи на UI.
 * Транслирует тикеры (событие "deal") и стаканы заявок (событие "orderbook")
 * в реальном времени всем подключённым клиентам.
 */
@Slf4j
public abstract class AbstractSseController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final String exchangeName;

    protected AbstractSseController(ExchangeAdapterFactory factory, ExchangeEnum exchange) {
        this.exchangeName = exchange.getDisplayName();
        var adapter = factory.getAdapter(exchange);

        adapter.onDeal(deal -> broadcast("deal", deal));
        adapter.onOrderBook(orderBook -> broadcast("orderbook", orderBook));
        adapter.onBookTicker(bookTicker -> broadcast("bookticker", bookTicker));

        log.info("{} initialized and subscribed to {}Adapter", getClass().getSimpleName(), exchangeName);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        var emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("{} SSE client disconnected (completed), remaining: {}", exchangeName, emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("{} SSE client disconnected (timeout), remaining: {}", exchangeName, emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("{} SSE client disconnected (error): {}", exchangeName, e.getMessage());
        });

        log.info("{} SSE client connected, total: {}", exchangeName, emitters.size());
        return emitter;
    }

    protected void broadcast(String eventName, Object data) {
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
                log.debug("{} SSE emitter removed due to error: {}", exchangeName, e.getMessage());
            }
        }
    }
}
