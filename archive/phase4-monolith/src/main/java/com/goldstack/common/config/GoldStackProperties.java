package com.goldstack.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "goldstack")
public record GoldStackProperties(
        @NotBlank String zone,
        List<String> corsOrigins,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Wallet wallet,
        @Valid @NotNull Quote quote,
        @Valid @NotNull Price price,
        @Valid @NotNull Sip sip,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull WhatIf whatIf,
        @Valid @NotNull Idempotency idempotency,
        @NotNull DevEndpoints devEndpoints) {

    public record Jwt(@NotBlank String secret, @Positive long expiryMinutes) {
    }

    public record Wallet(@NotNull @Positive BigDecimal signupBonus, @NotNull @Positive BigDecimal maxTopup) {
    }

    public record Quote(@Positive long validitySeconds) {
    }

    public record Price(@NotBlank String provider,
                        @Positive long tickMs,
                        @Positive int seedYears,
                        @NotNull @Positive BigDecimal seedEndPrice,
                        Api api) {
        public record Api(String url, String key) {
        }
    }

    public record Sip(@NotBlank String cron) {
    }

    public record RateLimit(@Positive int requestsPerMinute, @Positive int anonymousPerMinute) {
    }

    public record WhatIf(@Positive long cacheTtlMinutes) {
    }

    public record Idempotency(@Positive long resultTtlHours) {
    }

    public record DevEndpoints(boolean enabled) {
    }
}
