package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.Ticker;

import java.util.function.Consumer;

/**
 * Абстракция над WebSocket-клиентом биржи.
 * Каждая биржа реализует этот интерфейс для подключения,
 * подписки на потоки и получения нормализованных данных.
 */
public interface ExchangeAdapter {

    /**
     * Устанавливает соединение с биржей по WebSocket.
     */
    void connect();

    /**
     * Закрывает соединение и освобождает ресурсы.
     */
    void disconnect();

    /**
     * Подписывается на переданные потоки (streams).
     * Формат streams зависит от конкретной биржи.
     */
    void subscribe(String... streams);

    /**
     * Возвращает true, если соединение установлено и активно.
     */
    boolean isConnected();

    /**
     * Возвращает биржу, с которой работает адаптер.
     */
    ExchangeEnum getExchange();

    /**
     * Регистрирует обработчик входящих тикеров.
     */
    void onTicker(Consumer<Ticker> handler);

    /**
     * Регистрирует обработчик обновлений стакана заявок.
     */
    void onOrderBook(Consumer<OrderBook> handler);

    /**
     * Удаляет все зарегистрированные обработчики.
     */
    void clearHandlers();
}
