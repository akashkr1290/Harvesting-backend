package com.harvestflow.api.dto.marketplace;

import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ListingDetailResponse(
        String id,
        String sellerId,
        String sellerName,
        UserRole sellerRole,
        String cropName,
        Double quantity,
        String unit,
        String quality,
        LocalDate harvestDate,
        String location,
        Double expectedPricePerUnit,
        String description,
        ListingStatus status,
        List<String> imageUrls,
        Integer primaryImageIndex,
        String verificationStatus,
        Instant createdAt,
        Instant publishedAt,
        Instant closedAt,
        Double availableQuantity
) {
    public static ListingDetailResponse from(ProduceListing l) {
        return new ListingDetailResponse(
                l.getId().toString(),
                l.getSeller().getId().toString(),
                l.getSellerName(),
                l.getSellerRole(),
                l.getCropName(),
                l.getQuantity(),
                l.getUnit(),
                l.getQuality(),
                l.getHarvestDate(),
                l.getLocation(),
                l.getExpectedPricePerUnit(),
                l.getDescription(),
                l.getStatus(),
                l.getImageUrls(),
                l.getPrimaryImageIndex(),
                l.getVerificationStatus(),
                l.getCreatedAt(),
                l.getPublishedAt(),
                l.getClosedAt(),
                l.getAvailableQuantity() != null ? l.getAvailableQuantity() : l.getQuantity()
        );
    }
}
