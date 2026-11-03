package com.goldstack.price;

import com.goldstack.common.Money;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "goldstack.price.provider", havingValue = "simulator", matchIfMissing = true)
class SimulatedPriceProvider implements PriceProvider {

    private static final BigDecimal VOLATILITY_PER_TICK = new BigDecimal("0.0004");

    private final Random random = new Random();

    @Override
    public BigDecimal fetchPricePerGram(BigDecimal lastPrice) {
        BigDecimal shock = BigDecimal.valueOf(random.nextGaussian()).multiply(VOLATILITY_PER_TICK);
        BigDecimal next = lastPrice.multiply(BigDecimal.ONE.add(shock), MathContext.DECIMAL64);
        return Money.price(next);
    }

    @Override
    public String sourceName() {
        return "SIMULATOR";
    }
}
