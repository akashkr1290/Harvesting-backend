package com.harvestflow.api.dto.user;

import com.harvestflow.api.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String name,
        String mobile,
        @NotBlank @Email String email,
        @NotNull UserRole role,
        String location
) {
}
