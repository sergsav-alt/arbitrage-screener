package com.arbitrage.screener.exchange.candle;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.Candle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Стратегия получения свечных данных (klines) конкретной биржи.
 * Каждая поддерживающая свечи биржа реализует этот интерфейс отдельно,
 * не затрагивая общий {@link com.arbitrage.screener.exchange.ExchangeAdapter}.
 */
public interface CandleProvider {

    /**
     * Биржа, которую обслуживает провайдер.
     */
    ExchangeEnum exchange();

    /**
     * Загружает историю свечей через REST API.
     *
     * @param symbol   торговый символ (например, BTC-USDC или BTCUSDC)
     * @param interval интервал свечи (например, 1m, 5m, 1h)
     * @param limit    количество свечей (обрезается до допустимого диапазона биржи)
     * @return список свечей в хронологическом порядке (от старых к новым)
     */
    List<Candle> fetchHistory(String symbol, String interval, int limit);

    /**
     * Открывает WebSocket-поток свечей и вызывает {@code onCandle} на каждое обновление.
     * Если соединение не удаётся восстановить (исчерпан лимит переподключений),
     * вызывается {@code onError} — потребитель может завершить свой канал (например, SSE).
     * Возвращает дескриптор подписки: его {@link AutoCloseable#close()} закрывает
     * соединение и освобождает связанные ресурсы.
     */
    AutoCloseable stream(String symbol, String interval, Consumer<Candle> onCandle, Consumer<Throwable> onError);
}
