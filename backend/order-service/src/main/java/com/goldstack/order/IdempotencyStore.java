package com.goldstack.order;

import com.goldstack.common.redis.RedisJson;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class IdempotencyStore {

    static final Duration PENDING_TTL = Duration.ofSeconds(30);

    record Entry(String state, UUID quoteId, UUID orderId) {
        boolean done() {
            return "DONE".equals(state);
        }
    }

    private final RedisJson redis;
    private final Duration resultTtl;

    IdempotencyStore(RedisJson redis, OrderProperties props) {
        this.redis = redis;
        this.resultTtl = Duration.ofHours(props.idempotencyTtlHours());
    }

    static String key(UUID userId, String idempotencyKey) {
        return "idem:" + userId + ":" + idempotencyKey;
    }

    Optional<Entry> find(UUID userId, String idempotencyKey) {
        return redis.get(key(userId, idempotencyKey), Entry.class);
    }

    boolean tryLock(UUID userId, String idempotencyKey) {
        return redis.setIfAbsent(key(userId, idempotencyKey), new Entry("PENDING", null, null), PENDING_TTL);
    }

    void done(UUID userId, String idempotencyKey, UUID quoteId, UUID orderId) {
        redis.set(key(userId, idempotencyKey), new Entry("DONE", quoteId, orderId), resultTtl);
    }

    void release(UUID userId, String idempotencyKey) {
        redis.delete(key(userId, idempotencyKey));
    }
}
