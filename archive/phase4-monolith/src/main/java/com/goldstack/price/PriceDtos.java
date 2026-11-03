package com.goldstack.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PriceDtos {

    private PriceDtos() {
    }

    public record LivePrice(BigDecimal pricePerGram, Instant recordedAt, String source) {
    }

    public record DailyPriceResponse(LocalDate date, BigDecimal closePrice) {
    }

    public record PriceUpdatedEvent(LivePrice price) {
    }
}
