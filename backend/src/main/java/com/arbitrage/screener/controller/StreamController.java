package com.arbitrage.screener.controller;

import com.arbitrage.screener.core.ArbitrageService;
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
@RequestMapping("/api/stream")
public class StreamController {

    private final List<SseEmitter> emitters;

    public StreamController(ArbitrageService arbitrageService) {
        this.emitters = new CopyOnWriteArrayList<>();

        arbitrageService.onScanComplete(opportunities -> {
            for (var emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("opportunities")
                            .data(opportunities, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    emitters.remove(emitter);
                    log.debug("SSE emitter removed due to error: {}", e.getMessage());
                }
            }
        });
    }

    @GetMapping("/opportunities")
    public SseEmitter stream() {
        var emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }
}
