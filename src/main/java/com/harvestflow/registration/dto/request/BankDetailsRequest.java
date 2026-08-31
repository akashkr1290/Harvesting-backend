package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record BankDetailsRequest(String registrationToken, @NotBlank String accountHolderName,
 @NotBlank String accountNumber, @NotBlank @Pattern(regexp="^[A-Za-z]{4}0[A-Za-z0-9]{6}$") String ifscCode,
 @NotBlank String bankName) {}
