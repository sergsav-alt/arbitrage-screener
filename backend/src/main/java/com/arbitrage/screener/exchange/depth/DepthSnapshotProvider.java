package com.arbitrage.screener.exchange.depth;

import com.arbitrage.screener.exchange.ExchangeEnum;

/**
 * Стратегия получения snapshot стакана ордеров для конкретной биржи через REST.
 * Каждая реализация инкапсулирует базовый URL, формат запроса и валидацию ответа.
 */
public interface DepthSnapshotProvider {

    /**
     * Биржа, которую обслуживает данный провайдер.
     */
    ExchangeEnum exchange();

    /**
     * Возвращает snapshot стакана с биржи.
     *
     * @param symbol торговый символ (в пользовательском формате, например BTC-USDT)
     * @param depth  глубина стакана
     * @return типизированный ответ биржи (форма зависит от биржи)
     * @throws ExchangeApiException если биржа вернула ошибку или пустой ответ
     */
    Object fetchSnapshot(String symbol, int depth);
}
