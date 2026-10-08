package com.harvestflow.api.dto.marketplace;

import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.enums.ListingStatus;

import java.time.LocalDate;

public record ListingSummaryResponse(
        String id,
        String cropName,
        Double quantity,
        String unit,
        String quality,
        LocalDate harvestDate,
        String location,
        Double expectedPricePerUnit,
        ListingStatus status,
        String sellerName,
        String primaryImageUrl,
        String verificationStatus,
        Double availableQuantity
) {
    public static ListingSummaryResponse from(ProduceListing l) {
        String primary = null;
        if (l.getPrimaryImageIndex() != null
                && l.getPrimaryImageIndex() >= 0
                && l.getPrimaryImageIndex() < l.getImageUrls().size()) {
            primary = l.getImageUrls().get(l.getPrimaryImageIndex());
        } else if (!l.getImageUrls().isEmpty()) {
            primary = l.getImageUrls().get(0);
        }
        return new ListingSummaryResponse(
                l.getId().toString(),
                l.getCropName(),
                l.getQuantity(),
                l.getUnit(),
                l.getQuality(),
                l.getHarvestDate(),
                l.getLocation(),
                l.getExpectedPricePerUnit(),
                l.getStatus(),
                l.getSellerName(),
                primary,
                l.getVerificationStatus(),
                l.getAvailableQuantity() != null ? l.getAvailableQuantity() : l.getQuantity()
        );
    }
}
