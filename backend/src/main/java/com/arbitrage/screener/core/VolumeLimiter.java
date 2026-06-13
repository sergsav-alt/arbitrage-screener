package com.arbitrage.screener.core;

import java.math.BigDecimal;

/**
 * Фильтрует арбитражные возможности по минимальному объёму сделки.
 */
public class VolumeLimiter {

    private final BigDecimal minVolumeUsdt;

    public VolumeLimiter(BigDecimal minVolumeUsdt) {
        this.minVolumeUsdt = minVolumeUsdt;
    }

    /**
     * Проверяет, превышает ли объём сделки минимальный порог.
     *
     * @param volumeUsdt объём сделки в USDT
     * @return true, если объём >= минимального порога
     */
    public boolean isAboveMinVolume(BigDecimal volumeUsdt) {
        return volumeUsdt.compareTo(minVolumeUsdt) >= 0;
    }

    /**
     * Возвращает эффективный объём сделки — не более maxVolume, но не менее minVolume.
     *
     * @param maxVolumeBase максимальный доступный объём базового актива
     * @param price         цена базового актива
     * @return эффективный объём в USDT
     */
    public BigDecimal getEffectiveVolume(BigDecimal maxVolumeBase, BigDecimal price) {
        var maxVolumeUsdt = maxVolumeBase.multiply(price);
        if (maxVolumeUsdt.compareTo(minVolumeUsdt) < 0) {
            return BigDecimal.ZERO;
        }
        return maxVolumeUsdt;
    }
}
