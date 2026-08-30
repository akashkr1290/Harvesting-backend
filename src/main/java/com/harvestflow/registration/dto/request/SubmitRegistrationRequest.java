package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.NotBlank;
public record SubmitRegistrationRequest(@NotBlank String registrationToken) {}
