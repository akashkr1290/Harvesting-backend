package com.harvestflow.api.dto.marketplace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitOfferRequest(
        @NotNull @Positive Double quantity,
        @NotNull @Positive Double pricePerUnit,
        @Size(max = 1000) String message
) {}
