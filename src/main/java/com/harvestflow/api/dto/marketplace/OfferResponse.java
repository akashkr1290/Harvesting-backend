package com.harvestflow.api.dto.marketplace;

import com.harvestflow.api.domain.entity.Offer;
import com.harvestflow.api.domain.enums.OfferStatus;
import com.harvestflow.api.domain.enums.UserRole;

import java.time.Instant;

public record OfferResponse(
        String id,
        String listingId,
        String cropName,
        String buyerId,
        String buyerName,
        UserRole buyerRole,
        Double quantity,
        Double pricePerUnit,
        String message,
        OfferStatus status,
        Instant createdAt,
        Instant decidedAt
) {
    public static OfferResponse from(Offer o) {
        return new OfferResponse(
                o.getId().toString(),
                o.getListing().getId().toString(),
                o.getListing().getCropName(),
                o.getBuyer().getId().toString(),
                o.getBuyerName(),
                o.getBuyerRole(),
                o.getQuantity(),
                o.getPricePerUnit(),
                o.getMessage(),
                o.getStatus(),
                o.getCreatedAt(),
                o.getDecidedAt()
        );
    }
}
