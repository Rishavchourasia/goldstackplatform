package com.goldstack.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
class GoldOrder {

    enum Source { MANUAL, SIP }

    enum Status { PENDING, COMPLETED, FAILED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal coins;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grams;

    @Column(name = "price_per_gram", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerGram;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GoldOrder() {
    }

    static GoldOrder completed(UUID id, UUID userId, Side side, Source source, UUID quoteId, String idempotencyKey,
                               BigDecimal coins, BigDecimal grams, BigDecimal pricePerGram, Instant now) {
        GoldOrder o = new GoldOrder();
        o.id = id;
        o.userId = userId;
        o.side = side;
        o.source = source;
        o.quoteId = quoteId;
        o.idempotencyKey = idempotencyKey;
        o.coins = coins;
        o.grams = grams;
        o.pricePerGram = pricePerGram;
        o.status = Status.COMPLETED;
        o.createdAt = now;
        return o;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Side getSide() {
        return side;
    }

    Source getSource() {
        return source;
    }

    UUID getQuoteId() {
        return quoteId;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    BigDecimal getCoins() {
        return coins;
    }

    BigDecimal getGrams() {
        return grams;
    }

    BigDecimal getPricePerGram() {
        return pricePerGram;
    }

    Status getStatus() {
        return status;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
