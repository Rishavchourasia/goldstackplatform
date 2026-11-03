package com.goldstack.price;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "goldstack.price")
public record PriceProperties(@NotBlank String provider,
                              @Positive long tickMs,
                              @Positive int seedYears,
                              @NotNull @Positive BigDecimal seedEndPrice,
                              Api api) {
    public record Api(String url, String key) {
    }
}
