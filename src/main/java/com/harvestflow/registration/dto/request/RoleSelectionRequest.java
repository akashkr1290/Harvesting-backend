package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.NotBlank;
public record RoleSelectionRequest(@NotBlank String registrationToken,@NotBlank String role) {}
