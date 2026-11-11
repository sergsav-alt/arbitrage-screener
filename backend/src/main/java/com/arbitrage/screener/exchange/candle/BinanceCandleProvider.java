package com.arbitrage.screener.exchange.candle;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.exchange.ExchangeApiException;
import com.arbitrage.screener.exchange.ReconnectingWebSocketClient;
import com.arbitrage.screener.model.Candle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.arbitrage.screener.util.FormatHelper.normalizeSymbol;
import static com.arbitrage.screener.util.ObjectMapperHelper.objectMapper;

/**
 * Провайдер свечей для биржи Binance (Spot).
 * <ul>
 *   <li>История: {@code GET https://api.binance.com/api/v3/klines}</li>
 *   <li>Поток: {@code wss://stream.binance.com:9443/ws/<symbol>@kline_<interval>}</li>
 * </ul>
 *
 * @see <a href="https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams#klinecandlestick-streams">Binance Kline Streams</a>
 */
@Slf4j
@Component
public class BinanceCandleProvider implements CandleProvider {

    private static final ObjectMapper MAPPER = objectMapper();

    private static final String WS_ENDPOINT = "wss://stream.binance.com:9443/ws/";
    private static final int MAX_LIMIT = 1000;
    private static final int MIN_LIMIT = 1;

    private static final int MAX_RECONNECT_ATTEMPTS = 8;
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;

    private static final Set<String> VALID_INTERVALS = Set.of(
            "1s", "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    private final RestClient restClient;

    public BinanceCandleProvider(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://api.binance.com")
                .build();
    }

    @Override
    public ExchangeEnum exchange() {
        return ExchangeEnum.BINANCE;
    }

    @Override
    public List<Candle> fetchHistory(String symbol, String interval, int limit) {
        validateInterval(interval);
        var normalized = normalizeSymbol(symbol);
        var boundedLimit = Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);

        var body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/klines")
                        .queryParam("symbol", normalized)
                        .queryParam("interval", interval)
                        .queryParam("limit", boundedLimit)
                        .build())
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            throw new ExchangeApiException("Binance klines returned empty response");
        }

        JsonNode response;
        try {
            response = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new ExchangeApiException("Failed to parse Binance klines response: " + e.getMessage());
        }
        if (!response.isArray()) {
            throw new ExchangeApiException("Binance klines returned unexpected response");
        }

        var candles = new ArrayList<Candle>(response.size());
        for (var kline : response) {
            candles.add(fromRestKline(kline));
        }
        return candles;
    }

    @Override
    public AutoCloseable stream(String symbol, String interval, Consumer<Candle> onCandle, Consumer<Throwable> onError) {
        validateInterval(interval);
        var normalized = normalizeSymbol(symbol);
        var uri = URI.create(WS_ENDPOINT + normalized.toLowerCase() + "@kline_" + interval);
        var name = "Binance-Kline-" + normalized + "-" + interval;

        var client = new ReconnectingWebSocketClient(
                uri,
                name,
                null,
                text -> handleKlineMessage(name, text, onCandle),
                onError,
                MAX_RECONNECT_ATTEMPTS,
                INITIAL_RECONNECT_DELAY_SECONDS,
                MAX_RECONNECT_DELAY_SECONDS
        );
        client.start();
        return client;
    }

    private void validateInterval(String interval) {
        if (interval == null || !VALID_INTERVALS.contains(interval)) {
            throw new IllegalArgumentException(
                    "Invalid interval: " + interval + ". Available: " + VALID_INTERVALS);
        }
    }

    /**
     * REST kline — массив: [openTime, open, high, low, close, volume, closeTime, ...].
     * Завершённость исторических свечей не гарантируется для последней записи,
     * но фронтенд перезапишет её данными из потока, поэтому помечаем как закрытую.
     */
    private static Candle fromRestKline(JsonNode kline) {
        return new Candle(
                kline.get(0).asLong() / 1000,
                new BigDecimal(kline.get(1).asText()),
                new BigDecimal(kline.get(2).asText()),
                new BigDecimal(kline.get(3).asText()),
                new BigDecimal(kline.get(4).asText()),
                new BigDecimal(kline.get(5).asText()),
                true
        );
    }

    /**
     * WS kline — {@code {"e":"kline", ..., "k":{"t":openTime,"o","h","l","c","v","x":closed}}}.
     */
    private static void handleKlineMessage(String name, String text, Consumer<Candle> onCandle) {
        try {
            var root = MAPPER.readTree(text);
            if (!"kline".equals(root.path("e").asText())) {
                return;
            }
            var k = root.get("k");
            var candle = new Candle(
                    k.get("t").asLong() / 1000,
                    new BigDecimal(k.get("o").asText()),
                    new BigDecimal(k.get("h").asText()),
                    new BigDecimal(k.get("l").asText()),
                    new BigDecimal(k.get("c").asText()),
                    new BigDecimal(k.get("v").asText()),
                    k.get("x").asBoolean()
            );
            onCandle.accept(candle);
        } catch (Exception e) {
            log.error("{}: failed to parse message: {}", name, e.getMessage());
        }
    }
}
