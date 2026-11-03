package com.goldstack.sip;

import com.goldstack.common.Frequency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SipDtos {

    private SipDtos() {
    }

    public record CreateSipRequest(
            @NotNull @DecimalMin("10.00") @DecimalMax("100000.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotNull Frequency frequency,
            @NotNull LocalDate startDate) {
    }

    public record SipResponse(UUID id, BigDecimal amount, Frequency frequency, LocalDate startDate,
                              LocalDate nextRunDate, String status, Instant createdAt) {
        static SipResponse from(Sip s) {
            return new SipResponse(s.getId(), s.getAmount(), s.getFrequency(), s.getStartDate(), s.getNextRunDate(),
                    s.getStatus().name(), s.getCreatedAt());
        }
    }

    public record SipExecutionResponse(UUID id, LocalDate runDate, String status, UUID orderId, String failureReason,
                                       Instant attemptedAt) {
        static SipExecutionResponse from(SipExecution e) {
            return new SipExecutionResponse(e.getId(), e.getRunDate(), e.getStatus().name(), e.getOrderId(),
                    e.getFailureReason(), e.getAttemptedAt());
        }
    }

    public record SipRunSummary(LocalDate runDate, int due, int succeeded, int failed, int skipped) {
    }

    public record SipDebitFailedEvent(UUID sipId, UUID userId, BigDecimal amount, LocalDate runDate, String reason) {
    }
}
