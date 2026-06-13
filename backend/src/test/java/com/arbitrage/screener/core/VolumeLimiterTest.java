package com.arbitrage.screener.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class VolumeLimiterTest {

    private VolumeLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new VolumeLimiter(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Should allow volume above minimum")
    void shouldAllowAboveMinVolume() {
        assertTrue(limiter.isAboveMinVolume(new BigDecimal("100")));
    }

    @Test
    @DisplayName("Should allow volume equal to minimum")
    void shouldAllowEqualMinVolume() {
        assertTrue(limiter.isAboveMinVolume(new BigDecimal("10")));
    }

    @Test
    @DisplayName("Should reject volume below minimum")
    void shouldRejectBelowMinVolume() {
        assertFalse(limiter.isAboveMinVolume(new BigDecimal("5")));
    }

    @Test
    @DisplayName("Should reject zero volume")
    void shouldRejectZeroVolume() {
        assertFalse(limiter.isAboveMinVolume(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should calculate effective volume above minimum")
    void shouldCalculateEffectiveVolume() {
        var effective = limiter.getEffectiveVolume(
                new BigDecimal("2.0"),
                new BigDecimal("50000.00")
        );
        assertEquals(0, new BigDecimal("100000.00").compareTo(effective));
    }

    @Test
    @DisplayName("Should return zero for volume below minimum")
    void shouldReturnZeroForBelowMinimum() {
        var effective = limiter.getEffectiveVolume(
                new BigDecimal("0.0001"),
                new BigDecimal("50000.00")
        );
        // 0.0001 * 50000 = 5, which is < 10
        assertEquals(0, BigDecimal.ZERO.compareTo(effective));
    }
}
