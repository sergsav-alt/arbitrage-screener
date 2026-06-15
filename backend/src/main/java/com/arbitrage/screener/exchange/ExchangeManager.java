package com.arbitrage.screener.exchange;

import com.arbitrage.screener.cache.OrderBookCache;
import com.arbitrage.screener.model.Deal;
import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.cache.DealCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер жизненного цикла подключений к биржам.
 * Поддерживает одновременные подключения к разным биржам (по одной на биржу).
 * При повторном подключении к той же бирже предыдущее соединение этой биржи отключается.
 */
@Slf4j
@Service
public class ExchangeManager {

    private final ExchangeAdapterFactory adapterFactory;
    private final DealCache dealCache;
    private final OrderBookCache orderBookCache;
    private final List<Runnable> onUpdateListeners;
    private final Set<ExchangeEnum> handlerRegistered;
    private final Map<ExchangeEnum, ActiveConnection> activeConnections;

    public ExchangeManager(ExchangeAdapterFactory adapterFactory,
                           DealCache dealCache,
                           OrderBookCache orderBookCache) {
        this.adapterFactory = adapterFactory;
        this.dealCache = dealCache;
        this.orderBookCache = orderBookCache;
        this.onUpdateListeners = new CopyOnWriteArrayList<>();
        this.handlerRegistered = ConcurrentHashMap.newKeySet();
        this.activeConnections = new ConcurrentHashMap<>();
    }

    /**
     * Подключается к указанной бирже и подписывается на переданный символ.
     * Если для этой биржи уже есть активное подключение, оно заменяется новым.
     * Подключения к другим биржам не затрагиваются.
     *
     * @return статус подключения
     */
    public Map<String, Object> connectExchange(ExchangeEnum exchange, String symbol) {
        log.info("Connecting to {} for symbol {}", exchange, symbol);

        disconnectExchange(exchange);

        var adapter = adapterFactory.getAdapter(exchange);
        ensureHandlersRegistered(adapter);

        adapter.subscribe(symbol);
        adapter.connect();

        activeConnections.put(exchange, new ActiveConnection(adapter, symbol));

        var connected = adapter.isConnected();
        log.info("{} connection initiated for {}, connected: {}", exchange, symbol, connected);

        var result = new LinkedHashMap<String, Object>();
        result.put("exchange", exchange.name());
        result.put("symbol", symbol);
        result.put("connected", connected);
        return result;
    }

    /**
     * Отключает указанную биржу.
     */
    public void disconnectExchange(ExchangeEnum exchange) {
        var connection = activeConnections.remove(exchange);
        if (connection != null) {
            log.info("Disconnecting {} from {}", exchange, connection.symbol());
            connection.adapter().disconnect();
        }
    }

    /**
     * Отключает все активные соединения (используется при остановке приложения).
     */
    public void disconnectAll() {
        log.info("Disconnecting all adapters");
        for (var exchange : activeConnections.keySet()) {
            disconnectExchange(exchange);
        }
        adapterFactory.disconnectAll();
    }

    /**
     * Регистрирует обработчики тикеров и стаканов для адаптера,
     * если они ещё не были зарегистрированы.
     */
    private void ensureHandlersRegistered(ExchangeAdapter adapter) {
        if (handlerRegistered.add(adapter.getExchange())) {
            adapter.onDeal(this::onDealReceived);
            adapter.onOrderBook(this::onOrderBookReceived);
            log.info("Handlers registered for {}", adapter.getExchange());
        }
    }

    /**
     * Регистрирует слушатель, вызываемый при получении нового тикера.
     */
    public void onUpdate(Runnable listener) {
        onUpdateListeners.add(listener);
    }

    /**
     * Возвращает статус всех бирж.
     */
    public Map<ExchangeEnum, Boolean> getStatus() {
        var status = new LinkedHashMap<ExchangeEnum, Boolean>();
        for (var ex : ExchangeEnum.values()) {
            var adapter = adapterFactory.getAdapter(ex);
            status.put(ex, adapter.isConnected());
        }
        return status;
    }

    public int getActiveCount() {
        return activeConnections.size();
    }

    public boolean isRunning() {
        return !activeConnections.isEmpty();
    }

    private void onDealReceived(Deal deal) {
        dealCache.put(deal);
        onUpdateListeners.forEach(Runnable::run);
    }

    private void onOrderBookReceived(OrderBook orderBook) {
        orderBookCache.put(orderBook);
    }

    private record ActiveConnection(ExchangeAdapter adapter, String symbol) {}
}
