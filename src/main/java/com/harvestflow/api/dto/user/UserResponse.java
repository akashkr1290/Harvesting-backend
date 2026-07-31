package com.harvestflow.api.dto.user;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.UserRole;

import java.time.Instant;

public record UserResponse(
        String id,
        String name,
        String mobile,
        String email,
        UserRole role,
        String location,
        String username,
        boolean active,
        boolean mustChangePassword,
        Instant lastActiveAt,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getName(),
                user.getMobile(),
                user.getEmail(),
                user.getRole(),
                user.getLocation(),
                user.getUsername(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getLastActiveAt(),
                user.getCreatedAt()
        );
    }
}
