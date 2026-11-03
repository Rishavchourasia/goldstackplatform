package com.goldstack.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void doubleWouldBeWrongButBigDecimalIsExact() {

        assertThat(0.1 + 0.2).isNotEqualTo(0.3);
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2"))).isEqualByComparingTo("0.3");
    }

    @Test
    void buyRoundsGramsDownToFourDecimals() {
        assertThat(Money.gramsForCoins(new BigDecimal("500.00"), new BigDecimal("7500.00")))
                .isEqualTo(new BigDecimal("0.0666"));
    }

    @Test
    void sellPayoutRoundsCoinsDown() {

        assertThat(Money.payoutForGrams(new BigDecimal("0.0333"), new BigDecimal("7777.77")))
                .isEqualTo(new BigDecimal("258.99"));
    }

    @Test
    void percentHandlesZeroBase() {
        assertThat(Money.percent(new BigDecimal("5"), BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(Money.percent(new BigDecimal("1"), new BigDecimal("3"))).isEqualTo(new BigDecimal("33.33"));
    }
}
