package com.goldstack.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int COIN_SCALE = 2;
    public static final int GRAM_SCALE = 4;
    public static final int PRICE_SCALE = 2;
    public static final int PERCENT_SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal coins(BigDecimal value) {
        return value.setScale(COIN_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal grams(BigDecimal value) {
        return value.setScale(GRAM_SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal gramsForCoins(BigDecimal coins, BigDecimal pricePerGram) {
        return coins.divide(pricePerGram, GRAM_SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal payoutForGrams(BigDecimal grams, BigDecimal pricePerGram) {
        return grams.multiply(pricePerGram).setScale(COIN_SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal valueOf(BigDecimal grams, BigDecimal pricePerGram) {
        return grams.multiply(pricePerGram).setScale(COIN_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }

        return part.multiply(HUNDRED).divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
