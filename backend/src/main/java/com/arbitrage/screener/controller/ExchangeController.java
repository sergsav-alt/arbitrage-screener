package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeManager;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Контроллер управления WebSocket-подключениями к биржам.
 * Поддерживает одновременные подключения к разным биржам.
 * При повторном подключении к той же бирже предыдущее соединение этой биржи отключается.
 */
@Slf4j
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeManager exchangeManager;

    /**
     * Подключается к указанной бирже и подписывается на переданный символ.
     * Если для этой биржи уже есть активное подключение, оно заменяется новым.
     * Подключения к другим биржам не затрагиваются.
     *
     * @param exchange название биржи (BINANCE, BINGX, MEXC)
     * @param symbol   торговый символ (например, BTCUSDT)
     * @return статус подключения
     */
    @GetMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(
            @RequestParam String exchange,
            @RequestParam String symbol
    ) {
        var exchangeEnum = parseExchange(exchange);
        var normalizedSymbol = symbol.toUpperCase().trim().substring(0, 3) + "USDT";

        log.info("REST request: connect to {} for symbol {}", exchangeEnum, normalizedSymbol);

        var result = exchangeManager.connectExchange(exchangeEnum, normalizedSymbol);

        var connected = (boolean) result.get("connected");
        if (connected) {
            result.put("message", "WebSocket connected and ready to receive data for " + normalizedSymbol);
            return ResponseEntity.ok(result);
        }

        result.put("message", "WebSocket connection initiated for " + normalizedSymbol + ". Waiting for connection...");
        return ResponseEntity.ok(result);
    }

    /**
     * Отключает указанную биржу.
     *
     * @param exchange название биржи (BINANCE, BINGX, MEXC)
     * @return статус отключения
     */
    @GetMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestParam String exchange) {
        var exchangeEnum = parseExchange(exchange);

        log.info("REST request: disconnect {}", exchangeEnum);

        exchangeManager.disconnectExchange(exchangeEnum);

        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("exchange", exchangeEnum.name());
        result.put("connected", false);
        result.put("message", "Disconnected from " + exchangeEnum.getDisplayName());
        return ResponseEntity.ok(result);
    }

    private ExchangeEnum parseExchange(String exchange) {
        try {
            return ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid exchange: " + exchange + ". Available: " + java.util.Arrays.toString(ExchangeEnum.values())
            );
        }
    }
}
