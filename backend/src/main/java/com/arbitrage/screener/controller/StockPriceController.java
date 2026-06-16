package com.arbitrage.screener.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class StockPriceController {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/api/stream/bingx1", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStockPrices() {
        // Create an emitter with a 30-second timeout
        SseEmitter emitter = new SseEmitter(30_000L);
        Random random = new Random();

        executor.execute(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    // Simulate generating a new mock stock price
                    double price = 100 + (random.nextDouble() * 50);
                    String data = String.format("{\"ticker\": \"AAPL\", \"price\": %.2f}", price);

                    // Send the data packet over HTTP stream
                    emitter.send(SseEmitter.event()
                            .name("stock-update") // Named event group
                            .data(data));         // JSON payload

                    Thread.sleep(2000); // Wait 2 seconds before the next update
                }
                emitter.complete(); // Nicely close connection after finishing data loop
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e); // Handle connection break or errors cleanly
            }
        });

        return emitter;
    }
}
