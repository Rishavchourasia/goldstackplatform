package com.goldstack.portfolio;

import com.goldstack.common.Frequency;
import com.goldstack.portfolio.WhatIfCalculator.LumpSumResult;
import com.goldstack.portfolio.WhatIfCalculator.SipResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WhatIfCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Lump sum: price doubled -> value doubled, +100%")
    void lumpSumDoubling() {
        LumpSumResult r = WhatIfCalculator.lumpSum(bd("10000"), LocalDate.of(2020, 1, 1), bd("4000.00"), bd("8000.00"));

        assertThat(r.grams()).isEqualTo(bd("2.5000"));
        assertThat(r.valueNow()).isEqualTo(bd("20000.00"));
        assertThat(r.profit()).isEqualTo(bd("10000.00"));
        assertThat(r.returnPercent()).isEqualTo(bd("100.00"));
    }

    @Test
    @DisplayName("Lump sum: grams are rounded DOWN, like a real purchase")
    void lumpSumRoundsGramsDown() {

        LumpSumResult r = WhatIfCalculator.lumpSum(bd("500"), LocalDate.of(2024, 5, 1), bd("7500.00"), bd("7500.00"));

        assertThat(r.grams()).isEqualTo(bd("0.0666"));
        assertThat(r.valueNow()).isEqualTo(bd("499.50"));
        assertThat(r.profit()).isEqualTo(bd("-0.50"));
        assertThat(r.returnPercent()).isEqualTo(bd("-0.10"));
    }

    @Test
    @DisplayName("Lump sum: price fell 25% -> -25%")
    void lumpSumLoss() {
        LumpSumResult r = WhatIfCalculator.lumpSum(bd("1000"), LocalDate.of(2023, 1, 1), bd("8000.00"), bd("6000.00"));

        assertThat(r.grams()).isEqualTo(bd("0.1250"));
        assertThat(r.valueNow()).isEqualTo(bd("750.00"));
        assertThat(r.returnPercent()).isEqualTo(bd("-25.00"));
    }

    @Test
    @DisplayName("SIP monthly: three installments at 1000, 2000, 4000; valued today at 4000")
    void sipMonthly() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        LocalDate today = LocalDate.of(2026, 3, 20);
        Map<LocalDate, BigDecimal> prices = Map.of(
                LocalDate.of(2026, 1, 15), bd("1000.00"),
                LocalDate.of(2026, 2, 15), bd("2000.00"),
                LocalDate.of(2026, 3, 15), bd("4000.00"));

        SipResult r = WhatIfCalculator.sip(bd("1000"), Frequency.MONTHLY, start, today, prices::get, bd("4000.00"));

        assertThat(r.installments()).isEqualTo(3);
        assertThat(r.totalInvested()).isEqualTo(bd("3000.00"));
        assertThat(r.totalGrams()).isEqualTo(bd("1.7500"));
        assertThat(r.valueNow()).isEqualTo(bd("7000.00"));
        assertThat(r.profit()).isEqualTo(bd("4000.00"));
        assertThat(r.returnPercent()).isEqualTo(bd("133.33"));

        assertThat(r.series()).hasSize(4);
        assertThat(r.series().get(1).invested()).isEqualTo(bd("2000.00"));
        assertThat(r.series().get(1).value()).isEqualTo(bd("3000.00"));
        assertThat(r.series().get(3).date()).isEqualTo(today);
        assertThat(r.series().get(3).value()).isEqualTo(bd("7000.00"));
    }

    @Test
    @DisplayName("SIP: an installment due today uses the live price, and no duplicate final point")
    void sipInstallmentToday() {
        LocalDate today = LocalDate.of(2026, 3, 1);
        SipResult r = WhatIfCalculator.sip(bd("100"), Frequency.DAILY, today.minusDays(1), today,
                d -> bd("100.00"), bd("200.00"));

        assertThat(r.installments()).isEqualTo(2);
        assertThat(r.totalGrams()).isEqualTo(bd("1.5000"));
        assertThat(r.valueNow()).isEqualTo(bd("300.00"));
        assertThat(r.series()).hasSize(2);
    }

    @Test
    @DisplayName("Monthly schedule doesn't drift after a short month (Jan 31 -> Feb 28 -> Mar 31)")
    void monthlyScheduleFromMonthEnd() {
        assertThat(Frequency.MONTHLY.scheduleBetween(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)))
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }
}
