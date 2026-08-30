package com.harvestflow.registration.dto.request;
import jakarta.validation.constraints.*;
public record AddressRequest(@NotBlank String registrationToken,@NotBlank String addressLine,@NotBlank String villageOrCity,@NotBlank String district,@NotBlank String state,@NotBlank @Pattern(regexp="^\\d{6}$") String pincode) {}
