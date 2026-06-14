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
@Table(name = "arbitrage_event", indexes = {
        @Index(name = "idx_arbitrage_event_symbol", columnList = "symbol"),
        @Index(name = "idx_arbitrage_event_detected_at", columnList = "detectedAt"),
        @Index(name = "idx_arbitrage_event_type", columnList = "type")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitrageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type;

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

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal slippage;

    @Column(nullable = false)
    private Instant detectedAt;
}
