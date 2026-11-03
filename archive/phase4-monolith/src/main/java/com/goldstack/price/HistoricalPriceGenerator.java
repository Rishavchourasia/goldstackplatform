package com.goldstack.price;

import com.goldstack.common.Money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

final class HistoricalPriceGenerator {

    private static final BigDecimal NOISE_MEMORY = new BigDecimal("0.98");
    private static final BigDecimal DAILY_SHOCK = new BigDecimal("0.007");

    private static final BigDecimal START_FRACTION = new BigDecimal("0.31");
    private static final long SEED = 42L;

    private HistoricalPriceGenerator() {
    }

    static Map<LocalDate, BigDecimal> generate(LocalDate from, LocalDate to, BigDecimal endPrice) {
        Random random = new Random(SEED);
        BigDecimal startPrice = endPrice.multiply(START_FRACTION);
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(from, to));

        Map<LocalDate, BigDecimal> prices = new LinkedHashMap<>();
        BigDecimal noise = BigDecimal.ZERO;
        for (long i = 0; i <= totalDays; i++) {

            BigDecimal progress = BigDecimal.valueOf(i).divide(BigDecimal.valueOf(totalDays), 10, RoundingMode.HALF_UP);
            BigDecimal trend = startPrice.add(endPrice.subtract(startPrice).multiply(progress));

            BigDecimal shock = BigDecimal.valueOf(random.nextGaussian()).multiply(DAILY_SHOCK);
            noise = noise.multiply(NOISE_MEMORY).add(shock).round(MathContext.DECIMAL64);

            BigDecimal price = trend.multiply(BigDecimal.ONE.add(noise));
            prices.put(from.plusDays(i), Money.price(price));
        }
        return prices;
    }
}
