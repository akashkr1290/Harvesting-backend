package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.*;
public record SendAadhaarOtpRequest(@NotBlank String registrationToken,@NotBlank @Pattern(regexp="^\\d{12}$") String aadhaarNumber) {}
