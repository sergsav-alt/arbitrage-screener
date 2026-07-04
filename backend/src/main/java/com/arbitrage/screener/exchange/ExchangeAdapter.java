package com.arbitrage.screener.exchange;

import com.arbitrage.screener.model.BookTicker;
import com.arbitrage.screener.model.Deal;
import com.arbitrage.screener.model.OrderBook;

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
     * Полностью завершает работу адаптера: закрывает соединение
     * и останавливает внутренний планировщик (реконнект/ping).
     * Вызывается при остановке приложения; после этого адаптер
     * не подлежит повторному использованию.
     */
    void shutdown();

    /**
     * Подписывается на переданные потоки (streams).
     * Формат streams зависит от конкретной биржи.
     */
    void subscribe(String symbol);

    /**
     * Возвращает true, если соединение установлено и активно.
     */
    boolean isConnected();

    /**
     * Возвращает биржу, с которой работает адаптер.
     */
    ExchangeEnum getExchange();

    /**
     * Регистрирует обработчик обновлений стакана заявок.
     */
    void onOrderBook(Consumer<OrderBook> handler);

    /**
     * Регистрирует обработчик агрегированных сделок (aggTrade).
     * По умолчанию нет операции — адаптеры, поддерживающие aggTrade, переопределяют.
     */
    void onDeal(Consumer<Deal> handler);

    void onBookTicker(Consumer<BookTicker> handler);

    /**
     * Удаляет все зарегистрированные обработчики.
     */
    void clearHandlers();
}
