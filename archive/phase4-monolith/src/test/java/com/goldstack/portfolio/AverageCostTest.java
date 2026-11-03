package com.goldstack.portfolio;

import com.goldstack.order.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AverageCostTest {

    @Test
    void sellingRemovesCostProportionally() {
        AverageCost h = AverageCost.empty()
                .apply(Side.BUY, new BigDecimal("1.0000"), new BigDecimal("7000.00"))
                .apply(Side.BUY, new BigDecimal("1.0000"), new BigDecimal("8000.00"));
        assertThat(h.invested()).isEqualTo(new BigDecimal("15000.00"));

        AverageCost after = h.apply(Side.SELL, new BigDecimal("0.5000"), new BigDecimal("4500.00"));
        assertThat(after.grams()).isEqualTo(new BigDecimal("1.5000"));
        assertThat(after.invested()).isEqualTo(new BigDecimal("11250.00"));
    }

    @Test
    void sellingEverythingResetsCost() {
        AverageCost h = AverageCost.empty()
                .apply(Side.BUY, new BigDecimal("0.0666"), new BigDecimal("500.00"))
                .apply(Side.SELL, new BigDecimal("0.0666"), new BigDecimal("520.00"));
        assertThat(h.grams()).isEqualByComparingTo("0");
        assertThat(h.invested()).isEqualByComparingTo("0");
    }
}
