package com.harvestflow.api.dto.user;

import com.harvestflow.api.domain.entity.LoginHistory;

import java.time.Instant;

public record LoginHistoryResponse(
        String username,
        Instant loggedInAt,
        String ipAddress,
        boolean success,
        String failureReason
) {
    public static LoginHistoryResponse from(LoginHistory entry) {
        return new LoginHistoryResponse(
                entry.getUsername(),
                entry.getLoggedInAt(),
                entry.getIpAddress(),
                entry.isSuccess(),
                entry.getFailureReason()
        );
    }
}
