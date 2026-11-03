package com.goldstack.common.security;

import java.util.UUID;

public record AuthUser(UUID id, String email) {
}
