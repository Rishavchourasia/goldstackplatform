package com.goldstack.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldstack.common.Money;
import com.goldstack.common.config.GoldStackProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "goldstack.price.provider", havingValue = "api")
class GoldApiPriceProvider implements PriceProvider {

    private final RestClient client;

    GoldApiPriceProvider(GoldStackProperties props, RestClient.Builder builder) {
        GoldStackProperties.Price.Api api = props.price().api();
        if (api == null || api.key() == null || api.key().isBlank()) {
            throw new IllegalStateException("goldstack.price.provider=api requires the GOLD_API_KEY env variable");
        }
        this.client = builder.baseUrl(api.url()).defaultHeader("x-access-token", api.key()).build();
    }

    @Override
    public BigDecimal fetchPricePerGram(BigDecimal lastPrice) {
        JsonNode body = client.get().retrieve().body(JsonNode.class);
        if (body == null || !body.hasNonNull("price_gram_24k")) {
            throw new IllegalStateException("Gold API response has no price_gram_24k");
        }

        return Money.price(body.get("price_gram_24k").decimalValue());
    }

    @Override
    public String sourceName() {
        return "API";
    }
}
