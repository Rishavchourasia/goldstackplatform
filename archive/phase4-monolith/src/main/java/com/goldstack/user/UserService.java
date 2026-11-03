package com.goldstack.user;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.security.JwtService;
import com.goldstack.user.UserDtos.AuthResponse;
import com.goldstack.user.UserDtos.LoginRequest;
import com.goldstack.user.UserDtos.SignupRequest;
import com.goldstack.user.UserDtos.UserResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password()),
                req.fullName().trim(), Instant.now(clock)));

        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getFullName()));

        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {

        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect"));
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    private AuthResponse authResponse(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, "Bearer", jwtService.expirySeconds(), UserResponse.from(user));
    }
}
