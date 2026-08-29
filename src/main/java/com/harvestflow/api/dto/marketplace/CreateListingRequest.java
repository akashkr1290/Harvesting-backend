package com.harvestflow.api.dto.marketplace;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateListingRequest(
        @NotBlank String cropName,
        @NotNull @Positive Double quantity,
        @NotBlank String unit,
        String quality,
        @NotNull LocalDate harvestDate,
        @NotBlank String location,
        @NotNull @Positive Double expectedPricePerUnit,
        @Size(max = 2000) String description
) {}
