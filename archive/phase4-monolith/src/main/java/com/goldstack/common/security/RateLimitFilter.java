package com.goldstack.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.config.GoldStackProperties;
import com.goldstack.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_SECONDS = 60;

    private static final RedisScript<List> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('TTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final GoldStackProperties.RateLimit limits;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RateLimitFilter(StringRedisTemplate redis, GoldStackProperties props, ObjectMapper mapper, Clock clock) {
        this.redis = redis;
        this.limits = props.rateLimit();
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.getPrincipal() instanceof AuthUser;
        String key = loggedIn
                ? "ratelimit:" + ((AuthUser) auth.getPrincipal()).id()
                : "ratelimit:ip:" + request.getRemoteAddr();
        int limit = loggedIn ? limits.requestsPerMinute() : limits.anonymousPerMinute();

        long count;
        long ttl;
        try {
            List<?> result = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(WINDOW_SECONDS));
            count = ((Number) result.get(0)).longValue();
            ttl = Math.max(1, ((Number) result.get(1)).longValue());
        } catch (DataAccessException e) {
            log.warn("Rate limiter skipped (Redis unavailable): {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

        if (count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(ttl));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(clock), 429, "RATE_LIMITED",
                    "Too many requests. Try again in " + ttl + " seconds.", request.getRequestURI(), List.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
