package com.harvestflow.api.dto.marketplace;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Same shape as create — a farmer edits any field while the listing is still a DRAFT. */
public record UpdateListingRequest(
        @NotBlank String cropName,
        @NotNull @Positive Double quantity,
        @NotBlank String unit,
        String quality,
        @NotNull LocalDate harvestDate,
        @NotBlank String location,
        @NotNull @Positive Double expectedPricePerUnit,
        @Size(max = 2000) String description
) {}
