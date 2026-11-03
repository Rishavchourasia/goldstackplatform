package com.goldstack.price;

import java.math.BigDecimal;

public interface PriceProvider {

    BigDecimal fetchPricePerGram(BigDecimal lastPrice);

    String sourceName();
}
