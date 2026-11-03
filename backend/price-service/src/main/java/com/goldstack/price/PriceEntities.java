package com.goldstack.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "price_history")
class PriceTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_per_gram", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerGram;

    @Column(nullable = false)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PriceTick() {
    }

    PriceTick(BigDecimal pricePerGram, String source, Instant recordedAt) {
        this.pricePerGram = pricePerGram;
        this.source = source;
        this.recordedAt = recordedAt;
    }

    BigDecimal getPricePerGram() {
        return pricePerGram;
    }

    String getSource() {
        return source;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }
}

@Entity
@Table(name = "daily_prices")
class DailyPrice {

    @Id
    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal closePrice;

    protected DailyPrice() {
    }

    LocalDate getPriceDate() {
        return priceDate;
    }

    BigDecimal getClosePrice() {
        return closePrice;
    }
}
