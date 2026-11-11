package com.arbitrage.screener.model;

import java.math.BigDecimal;

/**
 * Нормализованная свеча (OHLCV) для отображения на графике.
 * Поле {@code time} — время открытия свечи в секундах Unix-эпохи
 * (формат, ожидаемый lightweight-charts на фронтенде).
 * Поле {@code closed} — признак того, что свеча завершена и больше не изменится.
 */
public record Candle(
        long time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean closed
) {}
