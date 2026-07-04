package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Единый SSE-контроллер для трансляции рыночных данных на UI по всем биржам.
 * Для каждой биржи ведётся отдельный список подключённых клиентов; обработчики
 * адаптеров рассылают события "deal", "orderbook" и "bookticker".
 * Эндпоинт: GET /api/stream/{exchange}
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final Map<ExchangeEnum, List<SseEmitter>> emittersByExchange = new EnumMap<>(ExchangeEnum.class);

    public StreamController(ExchangeAdapterFactory factory) {
        for (var exchange : ExchangeEnum.values()) {
            emittersByExchange.put(exchange, new CopyOnWriteArrayList<>());

            var adapter = factory.getAdapter(exchange);
            adapter.onDeal(deal -> broadcast(exchange, "deal", deal));
            adapter.onOrderBook(orderBook -> broadcast(exchange, "orderbook", orderBook));
            adapter.onBookTicker(bookTicker -> broadcast(exchange, "bookticker", bookTicker));

            log.info("StreamController subscribed to {}Adapter", exchange.getDisplayName());
        }
    }

    @GetMapping(value = "/{exchange}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String exchange) {
        var exchangeEnum = ExchangeEnum.fromString(exchange);
        var emitters = emittersByExchange.get(exchangeEnum);

        var emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("{} SSE client disconnected (completed), remaining: {}", exchangeEnum, emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("{} SSE client disconnected (timeout), remaining: {}", exchangeEnum, emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("{} SSE client disconnected (error): {}", exchangeEnum, e.getMessage());
        });

        log.info("{} SSE client connected, total: {}", exchangeEnum, emitters.size());
        return emitter;
    }

    private void broadcast(ExchangeEnum exchange, String eventName, Object data) {
        var emitters = emittersByExchange.get(exchange);
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
                log.debug("{} SSE emitter removed due to error: {}", exchange, e.getMessage());
            }
        }
    }
}
