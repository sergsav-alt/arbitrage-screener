package com.arbitrage.screener.util;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.OrderBookLevel;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.EMPTY;

public class FormatHelper {
    public static String formatSymbol(String symbol, ExchangeEnum exchange) {
        if (ExchangeEnum.BINANCE.equals(exchange)) {
            return symbol.toLowerCase().substring(0, 3) + "usdc";
        }
        if (ExchangeEnum.BINGX.equals(exchange)) {
            return symbol.toUpperCase().substring(0, 3) + "-USDT";
        }
        if (ExchangeEnum.MEXC.equals(exchange)) {
            return symbol.toUpperCase().substring(0, 3) + "_USDC";
        }
        return EMPTY;
    }

    public static List<OrderBookLevel> getOrderBookLevels(JsonNode levels) {
        var result = new ArrayList<OrderBookLevel>();
        for (var level : levels) {
            var price = new BigDecimal(level.get(0).asText());
            var qty = new BigDecimal(level.get(1).asText());
            result.add(new OrderBookLevel(price, qty));
        }
        return result;
    }

    public static void buildAndBroadcastOrderBook(ExchangeEnum exchange, String symbol,
                                                  JsonNode bidsNode, JsonNode asksNode,
                                                  Long timestamp, List<Consumer<OrderBook>> handlers) {
        var bids = getOrderBookLevels(bidsNode);
        var asks = getOrderBookLevels(asksNode);
        var orderBook = new OrderBook(exchange, symbol, bids, asks, timestamp);
        handlers.forEach(h -> h.accept(orderBook));
    }

    public static Long parseEventTime(JsonNode data) {
        if (data.has("T")) {
            return data.get("T").asLong();
        }
        if (data.has("E")) {
            return data.get("E").asLong();
        }
        return 0L;
    }

    public static Long parseTimestamp(JsonNode data) {
        return data.has("ts") ? data.get("ts").asLong() : 0L;
    }

    /**
     * Нормализует символ: удаляет дефисы, приводит к верхнему регистру.
     * BTC-USDT → BTCUSDT
     */
    public static String normalizeSymbol(String symbol) {
        return symbol.replace("-", "").toUpperCase();
    }

}
