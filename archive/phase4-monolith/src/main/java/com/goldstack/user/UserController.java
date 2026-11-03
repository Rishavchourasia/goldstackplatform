package com.goldstack.user;

import com.goldstack.common.security.AuthUser;
import com.goldstack.user.UserDtos.AuthResponse;
import com.goldstack.user.UserDtos.LoginRequest;
import com.goldstack.user.UserDtos.SignupRequest;
import com.goldstack.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth & Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Create an account. New users receive a signup bonus of virtual coins.")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        return userService.signup(req);
    }

    @PostMapping("/api/auth/login")
    @SecurityRequirements
    @Operation(summary = "Log in and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req);
    }

    @GetMapping("/api/users/me")
    @Operation(summary = "Current user's profile")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.getProfile(user.id());
    }
}
