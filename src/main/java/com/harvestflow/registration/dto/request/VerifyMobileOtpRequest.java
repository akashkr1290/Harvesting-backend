package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.*;
public record VerifyMobileOtpRequest(@NotBlank String registrationToken,@NotBlank @Pattern(regexp="^\\d{6}$") String otp) {}
