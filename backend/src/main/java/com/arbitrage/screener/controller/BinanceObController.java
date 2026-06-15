package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.BinanceDepthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.arbitrage.screener.util.FormatHelper.formatSymbol;

/**
 * Контроллер для получения snapshot стакана ордеров Binance (USD-M Futures) через REST API.
 * Эндпоинт: GET /api/binance/depth?symbol=BTCUSDT&limit=20
 *
 * @see <a href="https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/rest-api/Order-Book">Binance API Docs</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/binance")
public class BinanceObController {

    private final RestClient restClient;

    public BinanceObController(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://fapi.binance.com")
                .build();
    }

    /**
     * Возвращает снимок стакана ордеров (depth) с Binance USD-M Futures.
     *
     * @param symbol торговая пара в формате BASEQUOTE (например, BTCUSDT)
     * @param depth  глубина стакана: 5, 10, 20, 50, 100, 500, 1000 (по умолчанию 20)
     * @return данные стакана или ошибку
     */
    @GetMapping("/ob")
    public ResponseEntity<?> getDepth(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int depth) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/depth")
                            .queryParam("symbol", formatSymbol(symbol, ExchangeEnum.BINANCE))
                            .queryParam("limit", depth)
                            .build())
                    .retrieve()
                    .body(BinanceDepthResponse.class);

            if (response == null) {
                log.warn("Binance depth API returned null response");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Binance API returned empty response"
                ));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch depth from Binance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch depth from Binance",
                    "details", e.getMessage()
            ));
        }
    }
}
