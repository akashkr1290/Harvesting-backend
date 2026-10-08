package com.harvestflow.registration.dto.request;

import jakarta.validation.constraints.*;

/** House/street line is optional: many farmers only know village, district and PIN code. */
public record AddressRequest(@NotBlank String registrationToken,
                             @Size(max = 255) String addressLine,
                             @NotBlank String villageOrCity,
                             @NotBlank String district,
                             @NotBlank String state,
                             @NotBlank @Pattern(regexp = "^\\d{6}$") String pincode) {
}
