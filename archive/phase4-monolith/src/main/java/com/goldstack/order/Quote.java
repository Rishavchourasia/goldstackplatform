package com.goldstack.order;

import com.goldstack.common.error.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Quote(UUID quoteId, UUID userId, Side side, BigDecimal pricePerGram, BigDecimal coins,
                    BigDecimal grams, Instant createdAt, Instant expiresAt) {

    static Quote create(UUID userId, Side side, BigDecimal pricePerGram, BigDecimal coins, BigDecimal grams,
                        Instant now, long validitySeconds) {
        return new Quote(UUID.randomUUID(), userId, side, pricePerGram, coins, grams, now,
                now.plusSeconds(validitySeconds));
    }

    void checkUsableFor(Side expectedSide, Instant now) {
        if (side != expectedSide) {
            throw ApiException.badRequest("QUOTE_SIDE_MISMATCH", "This is a " + side + " quote");
        }

        if (!now.isBefore(expiresAt)) {
            throw ApiException.gone("QUOTE_EXPIRED", "This quote expired at " + expiresAt + ". Request a new quote.");
        }
    }
}
