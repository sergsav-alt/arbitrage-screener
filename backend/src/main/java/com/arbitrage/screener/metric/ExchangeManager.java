package com.arbitrage.screener.metric;

import com.arbitrage.screener.model.OrderBook;
import com.arbitrage.screener.model.Ticker;
import com.arbitrage.screener.model.TickerCache;
import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
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
 * В каждый момент времени активен только один адаптер (одна биржа, один символ).
 * При новом запросе на подключение предыдущее соединение отключается.
 */
@Slf4j
@Service
public class ExchangeManager {

    private final ExchangeAdapterFactory adapterFactory;
    private final TickerCache tickerCache;
    private final OrderBookCache orderBookCache;
    private final List<Runnable> onUpdateListeners;
    private final Set<ExchangeEnum> handlerRegistered;

    private volatile ExchangeAdapter currentAdapter;
    private volatile String currentSymbol;

    public ExchangeManager(ExchangeAdapterFactory adapterFactory,
                           TickerCache tickerCache,
                           OrderBookCache orderBookCache) {
        this.adapterFactory = adapterFactory;
        this.tickerCache = tickerCache;
        this.orderBookCache = orderBookCache;
        this.onUpdateListeners = new CopyOnWriteArrayList<>();
        this.handlerRegistered = ConcurrentHashMap.newKeySet();
    }

    /**
     * Подключается к указанной бирже и подписывается на переданный символ.
     * Все предыдущие подключения отключаются.
     *
     * @return статус подключения
     */
    public Map<String, Object> connectExchange(ExchangeEnum exchange, String symbol) {
        log.info("Connecting to {} for symbol {}", exchange, symbol);

        disconnectCurrent();

        var adapter = adapterFactory.getAdapter(exchange);
        ensureHandlersRegistered(adapter);

        adapter.subscribe(symbol);
        adapter.connect();

        currentAdapter = adapter;
        currentSymbol = symbol;

        var connected = adapter.isConnected();
        log.info("{} connection initiated for {}, connected: {}", exchange, symbol, connected);

        var result = new LinkedHashMap<String, Object>();
        result.put("exchange", exchange.name());
        result.put("symbol", symbol);
        result.put("connected", connected);
        return result;
    }

    /**
     * Отключает текущее активное соединение, не затрагивая зарегистрированные обработчики.
     */
    public void disconnectCurrent() {
        if (currentAdapter != null) {
            log.info("Disconnecting {} from {}", currentAdapter.getExchange(), currentSymbol);
            currentAdapter.disconnect();
            currentAdapter = null;
            currentSymbol = null;
        }
    }

    /**
     * Отключает все адаптеры (используется при остановке приложения).
     */
    public void disconnectAll() {
        log.info("Disconnecting all adapters");
        disconnectCurrent();
        adapterFactory.disconnectAll();
    }

    /**
     * Регистрирует обработчики тикеров и стаканов для адаптера,
     * если они ещё не были зарегистрированы.
     */
    private void ensureHandlersRegistered(ExchangeAdapter adapter) {
        if (handlerRegistered.add(adapter.getExchange())) {
            adapter.onTicker(this::onTickerReceived);
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
        return currentAdapter != null ? 1 : 0;
    }

    public boolean isRunning() {
        return currentAdapter != null && currentAdapter.isConnected();
    }

    private void onTickerReceived(Ticker ticker) {
        tickerCache.put(ticker);
        onUpdateListeners.forEach(Runnable::run);
    }

    private void onOrderBookReceived(OrderBook orderBook) {
        orderBookCache.put(orderBook);
    }
}
