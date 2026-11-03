package com.goldstack.gateway;

import com.goldstack.common.security.AuthUser;
import com.goldstack.common.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class GatewayFilters {

    static final String USER_ATTR = "goldstack.user";

    private GatewayFilters() {
    }

    static Mono<Void> writeError(ServerWebExchange exchange, Clock clock, HttpStatus status, String code, String message) {
        ServerHttpResponse res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"timestamp\":\"" + Instant.now(clock) + "\",\"status\":" + status.value()
                + ",\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"path\":\""
                + exchange.getRequest().getPath().value() + "\",\"fieldErrors\":[]}";
        DataBuffer buf = res.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buf));
    }
}

@Component
class DownstreamErrorFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorFilter.class);
    private final Clock clock;

    DownstreamErrorFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).onErrorResume(e -> {
            if (exchange.getResponse().isCommitted() || !isUnreachable(e)) {
                return Mono.error(e);
            }
            log.warn("Service unreachable for {}: {}", exchange.getRequest().getPath(), e.toString());
            return GatewayFilters.writeError(exchange, clock, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "This part of GoldStack is temporarily unavailable. Please try again in a moment.");
        });
    }

    private static boolean isUnreachable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.util.concurrent.TimeoutException
                    || t instanceof io.netty.channel.ConnectTimeoutException
                    || t.getClass().getSimpleName().contains("ConnectException")) {
                return true;
            }
        }
        return false;
    }
}

@Component
class AuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final List<String> PUBLIC_ANY = List.of("/api/auth/**");
    private static final List<String> PUBLIC_GET = List.of("/api/prices/**", "/api/what-if/**");

    private final JwtService jwt;
    private final Clock clock;

    AuthFilter(JwtService jwt, Clock clock) {
        this.jwt = jwt;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getPath().value();

        Optional<AuthUser> user = Optional.ofNullable(req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(h -> h.startsWith("Bearer "))
                .flatMap(h -> jwt.verify(h.substring(7)));
        user.ifPresent(u -> exchange.getAttributes().put(GatewayFilters.USER_ATTR, u));

        boolean isPublic = PUBLIC_ANY.stream().anyMatch(p -> PATHS.match(p, path))
                || (req.getMethod() == HttpMethod.GET && PUBLIC_GET.stream().anyMatch(p -> PATHS.match(p, path)));
        if (!isPublic && user.isEmpty()) {
            return GatewayFilters.writeError(exchange, clock, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Missing or invalid login token");
        }
        return chain.filter(exchange);
    }
}

@Component
class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String WINDOW_SECONDS = "60";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('TTL', KEYS[1])}
            """, List.class);

    private final ReactiveStringRedisTemplate redis;
    private final int userLimit;
    private final int anonymousLimit;
    private final Clock clock;

    RateLimitFilter(ReactiveStringRedisTemplate redis,
                    @Value("${goldstack.rate-limit.requests-per-minute}") int userLimit,
                    @Value("${goldstack.rate-limit.anonymous-per-minute}") int anonymousLimit, Clock clock) {
        this.redis = redis;
        this.userLimit = userLimit;
        this.anonymousLimit = anonymousLimit;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        AuthUser user = exchange.getAttribute(GatewayFilters.USER_ATTR);
        String key = user != null ? "ratelimit:" + user.id() : "ratelimit:ip:" + clientIp(exchange.getRequest());
        int limit = user != null ? userLimit : anonymousLimit;

        Mono<Optional<List<Object>>> counter = redis.execute(INCR_WITH_TTL, List.of(key), List.of(WINDOW_SECONDS))
                .next()
                .map(result -> Optional.of((List<Object>) result))
                .onErrorResume(e -> {
                    log.warn("Rate limiter skipped (Redis unavailable): {}", e.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());

        return counter
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return chain.filter(exchange);
                    }
                    List<Object> r = opt.get();
                    long count = ((Number) r.get(0)).longValue();
                    long ttl = Math.max(1, ((Number) r.get(1)).longValue());
                    HttpHeaders h = exchange.getResponse().getHeaders();
                    h.set("X-RateLimit-Limit", String.valueOf(limit));
                    h.set("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
                    if (count > limit) {
                        h.set("Retry-After", String.valueOf(ttl));
                        return GatewayFilters.writeError(exchange, clock, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                                "Too many requests. Try again in " + ttl + " seconds.");
                    }
                    return chain.filter(exchange);
                });
    }

    private static String clientIp(ServerHttpRequest req) {
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remote = req.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
