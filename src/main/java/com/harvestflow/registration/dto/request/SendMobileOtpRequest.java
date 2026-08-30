package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.*;
public record SendMobileOtpRequest(@NotBlank String registrationToken,@NotBlank @Pattern(regexp="^[6-9]\\d{9}$") String mobileNumber) {}
