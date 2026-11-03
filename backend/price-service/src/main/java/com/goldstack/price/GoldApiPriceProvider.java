package com.goldstack.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldstack.common.Money;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "goldstack.price.provider", havingValue = "api")
class GoldApiPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(GoldApiPriceProvider.class);

    private final RestClient client;

    GoldApiPriceProvider(PriceProperties props, RestClient.Builder builder) {
        PriceProperties.Api api = props.api();
        if (api == null || api.key() == null || api.key().isBlank()) {
            throw new IllegalStateException("goldstack.price.provider=api requires the GOLD_API_KEY env variable");
        }
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(api.url()).defaultHeader("x-access-token", api.key())
                .requestFactory(timeouts).build();
    }

    @Override
    @CircuitBreaker(name = "goldApi", fallbackMethod = "lastKnownPrice")
    public BigDecimal fetchPricePerGram(BigDecimal lastPrice) {
        JsonNode body = client.get().retrieve().body(JsonNode.class);
        if (body == null || !body.hasNonNull("price_gram_24k")) {
            throw new IllegalStateException("Gold API response has no price_gram_24k");
        }

        return Money.price(body.get("price_gram_24k").decimalValue());
    }

    BigDecimal lastKnownPrice(BigDecimal lastPrice, Throwable cause) {
        log.warn("Gold API unavailable ({}), keeping last price {}", cause.toString(), lastPrice);
        return lastPrice;
    }

    @Override
    public String sourceName() {
        return "API";
    }
}
