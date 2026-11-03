package com.goldstack.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "goldstack")
public record CommonProperties(
        @NotBlank String zone,
        @Valid @NotNull Jwt jwt,
        @Valid Security security,
        @Valid Outbox outbox) {

    public CommonProperties {
        security = security == null ? new Security(List.of(), List.of()) : security;
        outbox = outbox == null ? new Outbox(true, 300) : outbox;
    }

    public record Jwt(@NotBlank String secret, @Positive long expiryMinutes) {
    }

    public record Security(List<String> publicPaths, List<String> publicGetPaths) {
        public Security {
            publicPaths = publicPaths == null ? List.of() : publicPaths;
            publicGetPaths = publicGetPaths == null ? List.of() : publicGetPaths;
        }
    }

    public record Outbox(boolean enabled, long pollMs) {
    }
}
