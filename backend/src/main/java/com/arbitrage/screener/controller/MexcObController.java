package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.MexcDepthResponse;
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
 * Контроллер для получения snapshot стакана ордеров MEXC (Futures) через REST API.
 * Эндпоинт: GET /api/mexc/depth?symbol=BTC_USDT&limit=20
 *
 * @see <a href="https://mexcdevelop.github.io/apidocs/contract_v1_en/#public-channels">MEXC API Docs</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/mexc")
public class MexcObController {

    private final RestClient restClient;

    public MexcObController(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://contract.mexc.com")
                .build();
    }

    /**
     * Возвращает снимок стакана ордеров (depth) с MEXC Futures.
     *
     * @param symbol торговая пара в формате BASE_QUOTE (например, BTC_USDT)
     * @param depth  глубина стакана (опционально, типовое значение без limit — ~1500 уровней)
     * @return данные стакана или ошибку
     */
    @GetMapping("/ob")
    public ResponseEntity<?> getDepth(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int depth
    ) {
        log.info("Fetching MEXC depth: symbol={}, limit={}", symbol, depth);

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/v1/contract/depth/" + formatSymbol(symbol, ExchangeEnum.MEXC))
                                .queryParam("limit", depth);
                        return builder.build(symbol);
                    })
                    .retrieve()
                    .body(MexcDepthResponse.class);

            if (response == null || !response.success() || response.code() != 0) {
                log.warn("MEXC depth API returned error: success={}, code={}",
                        response != null && response.success(),
                        response != null ? response.code() : -1);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "MEXC API returned an error",
                        "code", response != null ? response.code() : -1
                ));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch depth from MEXC: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch depth from MEXC",
                    "details", e.getMessage()
            ));
        }
    }
}
