package com.goldstack.common.security;

import com.goldstack.common.config.GoldStackProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;
    private final Clock clock;

    public JwtService(GoldStackProperties props, Clock clock) {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(props.jwt().secret());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be base64. Generate one with: openssl rand -base64 32", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiry = Duration.ofMinutes(props.jwt().expiryMinutes());
        this.clock = clock;
    }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    public long expirySeconds() {
        return expiry.toSeconds();
    }

    public Optional<AuthUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(UUID.fromString(claims.getSubject()), claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
