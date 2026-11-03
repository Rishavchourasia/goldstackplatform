package com.goldstack.common.events;

import com.goldstack.common.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Events {

    private Events() {
    }

    public interface DomainEvent {
        UUID eventId();
    }

    public record UserRegistered(UUID eventId, Instant occurredAt, UUID userId, String email, String fullName)
            implements DomainEvent {
    }

    public record PriceUpdated(UUID eventId, Instant occurredAt, BigDecimal pricePerGram, Instant recordedAt,
                               String source) implements DomainEvent {
    }

    public record OrderPlaced(UUID eventId, Instant occurredAt, UUID orderId, UUID userId, Side side, String source,
                              BigDecimal coins, BigDecimal grams, BigDecimal pricePerGram, UUID sipId,
                              LocalDate runDate) implements DomainEvent {
    }

    public record WalletSettled(UUID eventId, Instant occurredAt, UUID orderId, UUID userId, Side side)
            implements DomainEvent {
    }

    public record WalletSettlementFailed(UUID eventId, Instant occurredAt, UUID orderId, UUID userId, Side side,
                                         String reasonCode, String reason) implements DomainEvent {
    }

    public record OrderCompleted(UUID eventId, Instant occurredAt, UUID orderId, UUID userId, Side side, String source,
                                 BigDecimal coins, BigDecimal grams, BigDecimal pricePerGram, UUID sipId,
                                 LocalDate runDate) implements DomainEvent {
    }

    public record OrderFailed(UUID eventId, Instant occurredAt, UUID orderId, UUID userId, Side side, String source,
                              BigDecimal coins, BigDecimal grams, String reasonCode, String reason,
                              boolean refundCoins) implements DomainEvent {
    }

    public record SipDebitRequested(UUID eventId, Instant occurredAt, UUID sipId, UUID userId, BigDecimal amount,
                                    LocalDate runDate, String idempotencyKey) implements DomainEvent {
    }

    public record SipDebitFailed(UUID eventId, Instant occurredAt, UUID sipId, UUID userId, BigDecimal amount,
                                 LocalDate runDate, UUID orderId, String reasonCode, String reason)
            implements DomainEvent {
    }

    public record NotificationRequested(UUID eventId, Instant occurredAt, UUID userId, String type, String message)
            implements DomainEvent {
    }
}
