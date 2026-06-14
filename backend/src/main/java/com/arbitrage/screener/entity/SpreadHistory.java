package com.arbitrage.screener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "spread_history", indexes = {
        @Index(name = "idx_spread_history_symbol", columnList = "symbol"),
        @Index(name = "idx_spread_history_recorded_at", columnList = "recordedAt"),
        @Index(name = "idx_spread_history_symbol_recorded", columnList = "symbol, recordedAt")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpreadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String buyExchange;

    @Column(nullable = false, length = 20)
    private String sellExchange;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal buyPrice;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal sellPrice;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal spreadPercent;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal netProfit;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal maxVolume;

    @Column(nullable = false)
    private Instant recordedAt;
}
