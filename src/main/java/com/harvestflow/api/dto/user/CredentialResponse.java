package com.harvestflow.api.dto.user;

public record CredentialResponse(
        UserResponse user,
        String temporaryPassword
) {
}
