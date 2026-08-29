package com.harvestflow.api.dto.marketplace;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Image URLs come from the existing {@code /api/uploads} flow; this just attaches them to a listing. */
public record AddListingImagesRequest(
        @NotEmpty List<String> imageUrls
) {}
