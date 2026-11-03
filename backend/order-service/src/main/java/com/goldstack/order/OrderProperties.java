package com.goldstack.order;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "goldstack.order")
public record OrderProperties(@Positive long quoteValiditySeconds, @Positive long idempotencyTtlHours,
                              @Positive long waitMs) {
}
