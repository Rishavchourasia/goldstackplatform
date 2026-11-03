package com.goldstack.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.error.ApiError;
import com.goldstack.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisSupportAutoConfiguration {

    @Bean
    public RedisJson redisJson(StringRedisTemplate redis, ObjectMapper mapper) {
        return new RedisJson(redis, mapper);
    }

    @Bean
    public RedisErrors redisErrors(GlobalExceptionHandler errors) {
        return new RedisErrors(errors);
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class RedisErrors {
        private static final Logger log = LoggerFactory.getLogger(RedisErrors.class);
        private final GlobalExceptionHandler errors;

        RedisErrors(GlobalExceptionHandler errors) {
            this.errors = errors;
        }

        @ExceptionHandler(RedisConnectionFailureException.class)
        public ResponseEntity<ApiError> redisDown(RedisConnectionFailureException ex, HttpServletRequest req) {
            log.error("Redis unavailable on {}: {}", req.getRequestURI(), ex.getMessage());
            return errors.build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "A required service is temporarily unavailable. Please try again shortly.", req, List.of());
        }
    }
}
