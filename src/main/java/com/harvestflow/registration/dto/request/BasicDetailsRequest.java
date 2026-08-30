package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.*;
public record BasicDetailsRequest(@NotBlank @Size(min=4,max=32) @Pattern(regexp="^[a-zA-Z0-9._]+$") String username,
 @NotBlank @Size(min=8,max=64) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$") String password,
 @NotBlank @Size(max=255) String fullName, @NotBlank @Email String email) {}
