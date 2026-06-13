package com.arbitrage.screener.core;

import com.arbitrage.screener.entity.Ticker;
import com.arbitrage.screener.entity.TickerCache;
import com.arbitrage.screener.exchange.ExchangeAdapter;
import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер жизненного цикла подключений к биржам.
 * Управляет стартом/стопом адаптеров, подписками и маршрутизацией тикеров в кэш.
 */
@Slf4j
@Service
public class ExchangeManager {

    private final ExchangeAdapterFactory adapterFactory;
    private final TickerCache tickerCache;
    private final List<ExchangeAdapter> activeAdapters;
    private final List<Runnable> onUpdateListeners;
    private volatile boolean running;

    public ExchangeManager(ExchangeAdapterFactory adapterFactory, TickerCache tickerCache) {
        this.adapterFactory = adapterFactory;
        this.tickerCache = tickerCache;
        this.activeAdapters = new CopyOnWriteArrayList<>();
        this.onUpdateListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Запускает подключения ко всем указанным биржам и подписывается на переданные символы.
     */
    public void start(List<ExchangeEnum> exchanges, List<String> symbols) {
        if (running) {
            log.warn("ExchangeManager is already running");
            return;
        }

        log.info("Starting ExchangeManager for exchanges: {}, symbols: {}", exchanges, symbols);

        for (var exchange : exchanges) {
            var adapter = adapterFactory.getAdapter(exchange);
            adapter.onTicker(this::onTickerReceived);
            adapter.subscribe(symbols.toArray(new String[0]));
            adapter.connect();
            activeAdapters.add(adapter);
        }

        running = true;
        log.info("ExchangeManager started with {} adapters", activeAdapters.size());
    }

    /**
     * Останавливает все активные подключения.
     */
    public void stop() {
        log.info("Stopping ExchangeManager");
        running = false;

        for (var adapter : activeAdapters) {
            adapter.clearHandlers();
            adapter.disconnect();
        }
        activeAdapters.clear();
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
        var status = new java.util.LinkedHashMap<ExchangeEnum, Boolean>();
        for (var adapter : activeAdapters) {
            status.put(adapter.getExchange(), adapter.isConnected());
        }
        return status;
    }

    /**
     * Возвращает количество активных адаптеров.
     */
    public int getActiveCount() {
        return activeAdapters.size();
    }

    public boolean isRunning() {
        return running;
    }

    private void onTickerReceived(Ticker ticker) {
        tickerCache.put(ticker);
        onUpdateListeners.forEach(Runnable::run);
    }
}
