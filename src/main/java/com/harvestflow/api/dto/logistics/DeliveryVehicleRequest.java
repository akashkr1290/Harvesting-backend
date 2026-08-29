package com.harvestflow.api.dto.logistics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DeliveryVehicleRequest(
        @NotBlank String registrationNumber,
        @NotBlank String vehicleType,
        @NotNull @Positive Double capacityKg
) {
}
