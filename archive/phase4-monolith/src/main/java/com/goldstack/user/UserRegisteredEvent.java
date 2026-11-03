package com.goldstack.user;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email, String fullName) {
}
