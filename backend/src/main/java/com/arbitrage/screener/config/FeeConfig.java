package com.arbitrage.screener.config;

import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация комиссий бирж и комиссий на вывод.
 * Загружается из application.yml (секция fees).
 */
@Data
@Component
@ConfigurationProperties(prefix = "fees")
public class FeeConfig {

    private Map<String, BigDecimal> takerPercent = new HashMap<>();
    private Withdrawal withdrawal = new Withdrawal();

    @Data
    public static class Withdrawal {
        private Map<String, Map<String, BigDecimal>> perSymbol = new HashMap<>();
        private BigDecimal defaultUsdt = BigDecimal.ZERO;
    }

    /**
     * Возвращает taker-комиссию в процентах для указанной биржи.
     */
    public BigDecimal getTakerFeePercent(ExchangeEnum exchange) {
        return takerPercent.getOrDefault(exchange.name().toLowerCase(), BigDecimal.ZERO);
    }

    /**
     * Возвращает комиссию на вывод для указанного символа с указанной биржи.
     * Если для символа не найдено — возвращает значение по умолчанию.
     */
    public BigDecimal getWithdrawalFee(String symbol, ExchangeEnum exchange) {
        String baseSymbol = extractBaseSymbol(symbol);
        String exchangeKey = exchange.name().toLowerCase();

        var bySymbol = withdrawal.getPerSymbol().get(baseSymbol);
        if (bySymbol != null && bySymbol.containsKey(exchangeKey)) {
            return bySymbol.get(exchangeKey);
        }
        return withdrawal.getDefaultUsdt();
    }

    private String extractBaseSymbol(String symbol) {
        var upper = symbol.toUpperCase();
        if (upper.endsWith("USDT")) return "USDT";
        if (upper.endsWith("BTC")) return "BTC";
        if (upper.endsWith("ETH")) return "ETH";
        return upper;
    }
}
