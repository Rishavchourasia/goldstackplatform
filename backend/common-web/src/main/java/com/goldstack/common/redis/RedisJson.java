package com.goldstack.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisJson {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisJson(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void set(String key, Object value, Duration ttl) {
        String json = write(value);
        if (ttl == null) {
            redis.opsForValue().set(key, json);
        } else {
            redis.opsForValue().set(key, json, ttl);
        }
    }

    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, write(value), ttl));
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt JSON in Redis key " + key, e);
        }
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redis.delete(key));
    }

    public Optional<Duration> ttl(String key) {
        Long seconds = redis.getExpire(key);
        return seconds == null || seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }
}
