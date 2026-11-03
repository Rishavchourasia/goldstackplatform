package com.goldstack.order;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.redis.RedisJson;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
class QuoteStore {

    private final RedisJson redis;

    QuoteStore(RedisJson redis) {
        this.redis = redis;
    }

    static String key(UUID quoteId) {
        return "quote:" + quoteId;
    }

    void save(Quote quote) {
        redis.set(key(quote.quoteId()), quote, Duration.between(quote.createdAt(), quote.expiresAt()));
    }

    Quote take(UUID userId, UUID quoteId, Side side, Instant now) {
        Quote quote = redis.get(key(quoteId), Quote.class)
                .filter(q -> q.userId().equals(userId))
                .orElseThrow(() -> ApiException.gone("QUOTE_EXPIRED",
                        "This quote has expired or was already used. Request a new quote."));
        quote.checkUsableFor(side, now);
        if (!redis.delete(key(quoteId))) {
            throw ApiException.gone("QUOTE_ALREADY_USED", "This quote was just used by another request.");
        }
        return quote;
    }

    void putBack(Quote quote, Instant now) {
        Duration left = Duration.between(now, quote.expiresAt());
        if (!left.isNegative() && !left.isZero()) {
            redis.set(key(quote.quoteId()), quote, left);
        }
    }
}
