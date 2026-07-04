package com.arbitrage.screener.exchange.depth;

/**
 * Сигнализирует, что внешний REST API биржи вернул ошибку или некорректный ответ.
 * Обрабатывается централизованно и маппится в HTTP 502 (Bad Gateway).
 */
public class ExchangeApiException extends RuntimeException {

    public ExchangeApiException(String message) {
        super(message);
    }
}
