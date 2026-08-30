package com.harvestflow.registration.dto.response;
public record OtpSentResponse(String status,int expiresInSeconds,int resendAvailableInSeconds) {}
