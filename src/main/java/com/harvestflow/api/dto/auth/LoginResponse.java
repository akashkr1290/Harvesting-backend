package com.harvestflow.api.dto.auth;

import com.harvestflow.api.domain.enums.UserRole;

public record LoginResponse(
        String token,
        String userId,
        String username,
        String name,
        UserRole role,
        boolean mustChangePassword
) {
}
