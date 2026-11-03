package com.goldstack.sip;

import com.goldstack.common.Frequency;
import com.goldstack.common.error.ApiException;
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
@Table(name = "sips")
class Sip {

    enum Status { ACTIVE, PAUSED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Sip() {
    }

    Sip(UUID userId, BigDecimal amount, Frequency frequency, LocalDate startDate, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.amount = amount;
        this.frequency = frequency;
        this.startDate = startDate;
        this.nextRunDate = startDate;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void pause(Instant now) {
        if (status != Status.ACTIVE) {
            throw ApiException.conflict("INVALID_SIP_STATE", "Only an ACTIVE SIP can be paused (it is " + status + ")");
        }
        status = Status.PAUSED;
        updatedAt = now;
    }

    void resume(LocalDate today, Instant now) {
        if (status != Status.PAUSED) {
            throw ApiException.conflict("INVALID_SIP_STATE", "Only a PAUSED SIP can be resumed (it is " + status + ")");
        }
        if (nextRunDate.isBefore(today)) {
            nextRunDate = frequency.firstOnOrAfter(startDate, today);
        }
        status = Status.ACTIVE;
        updatedAt = now;
    }

    void cancel(Instant now) {
        if (status == Status.CANCELLED) {
            throw ApiException.conflict("INVALID_SIP_STATE", "SIP is already cancelled");
        }
        status = Status.CANCELLED;
        updatedAt = now;
    }

    void advancePast(LocalDate runDate, Instant now) {
        nextRunDate = frequency.firstAfter(startDate, runDate);
        updatedAt = now;
    }

    boolean isDueOn(LocalDate date) {
        return status == Status.ACTIVE && !nextRunDate.isAfter(date);
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Frequency getFrequency() {
        return frequency;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getNextRunDate() {
        return nextRunDate;
    }

    Status getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

@Entity
@Table(name = "sip_executions")
class SipExecution {

    enum Status { PENDING, SUCCESS, FAILED }

    @Id
    private UUID id;

    @Column(name = "sip_id", nullable = false)
    private UUID sipId;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected SipExecution() {
    }

    SipExecution(UUID sipId, LocalDate runDate, String idempotencyKey, UUID orderId, Status status,
                 String failureReason, Instant attemptedAt) {
        this.id = UUID.randomUUID();
        this.sipId = sipId;
        this.runDate = runDate;
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.status = status;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    static SipExecution pending(UUID sipId, LocalDate runDate, String idempotencyKey, Instant now) {
        return new SipExecution(sipId, runDate, idempotencyKey, null, Status.PENDING, null, now);
    }

    void succeed(UUID orderId, Instant now) {
        if (status == Status.PENDING) {
            status = Status.SUCCESS;
            this.orderId = orderId;
            attemptedAt = now;
        }
    }

    void fail(UUID orderId, String reason, Instant now) {
        if (status == Status.PENDING) {
            status = Status.FAILED;
            this.orderId = orderId;
            failureReason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
            attemptedAt = now;
        }
    }

    UUID getId() {
        return id;
    }

    LocalDate getRunDate() {
        return runDate;
    }

    UUID getOrderId() {
        return orderId;
    }

    Status getStatus() {
        return status;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getAttemptedAt() {
        return attemptedAt;
    }
}
