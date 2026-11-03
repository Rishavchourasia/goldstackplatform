package com.goldstack.portfolio;

import com.goldstack.common.Money;
import com.goldstack.order.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AverageCost(BigDecimal grams, BigDecimal invested) {

    public static AverageCost empty() {
        return new AverageCost(new BigDecimal("0.0000"), new BigDecimal("0.00"));
    }

    public AverageCost apply(Side side, BigDecimal orderGrams, BigDecimal orderCoins) {
        if (side == Side.BUY) {
            return new AverageCost(grams.add(orderGrams), invested.add(orderCoins));
        }
        if (grams.signum() == 0) {
            return this;
        }
        BigDecimal remainingGrams = grams.subtract(orderGrams);
        if (remainingGrams.signum() <= 0) {
            return empty();
        }

        BigDecimal costOfSold = invested.multiply(orderGrams)
                .divide(grams, Money.COIN_SCALE, RoundingMode.HALF_UP);
        return new AverageCost(remainingGrams, invested.subtract(costOfSold));
    }
}
