package com.goldstack.portfolio;

import com.goldstack.common.Frequency;
import com.goldstack.common.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class WhatIfCalculator {

    private WhatIfCalculator() {
    }

    public record LumpSumResult(BigDecimal amount, LocalDate investedOn, BigDecimal priceThen, BigDecimal priceNow,
                                BigDecimal grams, BigDecimal valueNow, BigDecimal profit, BigDecimal returnPercent) {
    }

    public record SeriesPoint(LocalDate date, BigDecimal invested, BigDecimal value) {
    }

    public record SipResult(BigDecimal amountPerInstallment, Frequency frequency, LocalDate startDate,
                            int installments, BigDecimal totalInvested, BigDecimal totalGrams, BigDecimal valueNow,
                            BigDecimal profit, BigDecimal returnPercent, List<SeriesPoint> series) {
    }

    public static LumpSumResult lumpSum(BigDecimal amount, LocalDate date, BigDecimal priceThen, BigDecimal priceNow) {
        BigDecimal coins = Money.coins(amount);
        BigDecimal grams = Money.gramsForCoins(coins, priceThen);
        BigDecimal valueNow = Money.valueOf(grams, priceNow);
        BigDecimal profit = valueNow.subtract(coins);
        return new LumpSumResult(coins, date, priceThen, priceNow, grams, valueNow, profit,
                Money.percent(profit, coins));
    }

    public static SipResult sip(BigDecimal amount, Frequency frequency, LocalDate startDate, LocalDate today,
                                Function<LocalDate, BigDecimal> priceOn, BigDecimal priceNow) {
        BigDecimal coins = Money.coins(amount);
        List<LocalDate> dates = frequency.scheduleBetween(startDate, today);

        BigDecimal totalGrams = new BigDecimal("0.0000");
        BigDecimal totalInvested = new BigDecimal("0.00");
        List<SeriesPoint> series = new ArrayList<>(dates.size() + 1);

        for (LocalDate date : dates) {
            BigDecimal price = date.equals(today) ? priceNow : priceOn.apply(date);
            totalGrams = totalGrams.add(Money.gramsForCoins(coins, price));
            totalInvested = totalInvested.add(coins);
            series.add(new SeriesPoint(date, totalInvested, Money.valueOf(totalGrams, price)));
        }

        BigDecimal valueNow = Money.valueOf(totalGrams, priceNow);
        if (series.isEmpty() || !series.get(series.size() - 1).date().equals(today)) {
            series.add(new SeriesPoint(today, totalInvested, valueNow));
        }
        BigDecimal profit = valueNow.subtract(totalInvested);
        return new SipResult(coins, frequency, startDate, dates.size(), totalInvested, totalGrams, valueNow, profit,
                Money.percent(profit, totalInvested), series);
    }
}
