package com.goldstack.order;

import com.goldstack.common.Side;
import com.goldstack.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteTest {

    private final Instant created = Instant.parse("2026-11-03T10:00:00Z");
    private final Quote quote = Quote.create(UUID.randomUUID(), Side.BUY, new BigDecimal("7500.00"),
            new BigDecimal("500.00"), new BigDecimal("0.0666"), created, 300);

    @Test
    void usableBeforeExpiry() {
        assertThatCode(() -> quote.checkUsableFor(Side.BUY, created.plusSeconds(299))).doesNotThrowAnyException();
    }

    @Test
    void expiredQuoteIsRejectedWith410() {
        assertThatThrownBy(() -> quote.checkUsableFor(Side.BUY, created.plusSeconds(300)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("QUOTE_EXPIRED");
                    org.assertj.core.api.Assertions.assertThat(e.getStatus().value()).isEqualTo(410);
                });
    }

    @Test
    void buyQuoteCannotBeUsedToSell() {
        assertThatThrownBy(() -> quote.checkUsableFor(Side.SELL, created))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("QUOTE_SIDE_MISMATCH"));
    }
}
