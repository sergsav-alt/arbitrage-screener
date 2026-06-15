package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.BingXDepthResponse;
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
 * Контроллер для получения snapshot стакана ордеров BingX через REST API.
 * Эндпоинт: GET /api/bingx/depth?symbol=BTC-USDT&limit=20
 *
 * @see <a href="https://bingx-api.github.io/docs/#/en-us/swapV2/market.html">BingX API Docs</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/bingx")
public class BingXObController {

    private final RestClient restClient;

    public BingXObController(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://open-api.bingx.com")
                .build();
    }

    /**
     * Возвращает снимок стакана ордеров (depth) с BingX.
     *
     * @param symbol торговая пара в формате BASE-QUOTE (например, BTC-USDT)
     * @param depth  глубина стакана: 5, 10, 20, 50, 100, 500, 1000 (по умолчанию 20)
     * @return данные стакана или ошибку
     */
    @GetMapping("/ob")
    public ResponseEntity<?> getDepth(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int depth
    ) {
        log.info("Fetching BingX depth: symbol={}, limit={}", symbol, depth);

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/openApi/swap/v2/quote/depth")
                            .queryParam("symbol", formatSymbol(symbol, ExchangeEnum.BINGX))
                            .queryParam("limit", depth)
                            .build())
                    .retrieve()
                    .body(BingXDepthResponse.class);

            if (response == null || response.code() != 0) {
                log.warn("BingX depth API returned error: code={}, msg={}",
                        response != null ? response.code() : -1,
                        response != null ? response.msg() : "null");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "BingX API returned an error",
                        "code", response != null ? response.code() : -1,
                        "msg", response != null ? response.msg() : "No response"
                ));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch depth from BingX: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch depth from BingX",
                    "details", e.getMessage()
            ));
        }
    }
}
