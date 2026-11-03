package com.goldstack.order;

import com.goldstack.common.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "sip_id")
    private UUID sipId;

    @Column(name = "run_date")
    private LocalDate runDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoldOrder() {
    }

    static GoldOrder pending(UUID userId, Side side, Source source, UUID quoteId, String idempotencyKey,
                             BigDecimal coins, BigDecimal grams, BigDecimal pricePerGram,
                             UUID sipId, LocalDate runDate, Instant now) {
        GoldOrder o = new GoldOrder();
        o.id = UUID.randomUUID();
        o.userId = userId;
        o.side = side;
        o.source = source;
        o.quoteId = quoteId;
        o.idempotencyKey = idempotencyKey;
        o.coins = coins;
        o.grams = grams;
        o.pricePerGram = pricePerGram;
        o.sipId = sipId;
        o.runDate = runDate;
        o.status = Status.PENDING;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }

    boolean isPending() {
        return status == Status.PENDING;
    }

    void complete(Instant now) {
        status = Status.COMPLETED;
        updatedAt = now;
    }

    void fail(String code, String reason, Instant now) {
        status = Status.FAILED;
        failureCode = code;
        failureReason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        updatedAt = now;
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

    String getFailureCode() {
        return failureCode;
    }

    String getFailureReason() {
        return failureReason;
    }

    UUID getSipId() {
        return sipId;
    }

    LocalDate getRunDate() {
        return runDate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
