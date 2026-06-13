package com.arbitrage.screener.exchange;

import com.arbitrage.screener.exchange.bingx.BingXAdapter;
import com.arbitrage.screener.exchange.mexc.MexcAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фабрика для создания и кэширования адаптеров бирж.
 * Каждый адаптер создаётся один раз и переиспользуется.
 */
public class ExchangeAdapterFactory {

    private final Map<ExchangeEnum, ExchangeAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * Возвращает адаптер для указанной биржи.
     * Создаёт новый экземпляр при первом обращении.
     */
    public ExchangeAdapter getAdapter(ExchangeEnum exchange) {
        return adapters.computeIfAbsent(exchange, this::createAdapter);
    }

    private ExchangeAdapter createAdapter(ExchangeEnum exchange) {
        return switch (exchange) {
            case BINGX -> new BingXAdapter();
            case MEXC -> new MexcAdapter();
        };
    }

    /**
     * Отключает все активные адаптеры.
     */
    public void disconnectAll() {
        adapters.values().forEach(ExchangeAdapter::disconnect);
    }
}
