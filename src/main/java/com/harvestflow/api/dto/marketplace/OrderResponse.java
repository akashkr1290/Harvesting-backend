package com.harvestflow.api.dto.marketplace;

import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.enums.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        String id,
        String listingId,
        String offerId,
        String cropName,
        String sellerId,
        String sellerName,
        String buyerId,
        String buyerName,
        Double quantity,
        Double agreedPricePerUnit,
        Double totalAmount,
        OrderStatus status,
        Instant createdAt
) {
    public static OrderResponse from(MarketOrder o) {
        return new OrderResponse(
                o.getId().toString(),
                o.getListing().getId().toString(),
                o.getOffer().getId().toString(),
                o.getCropName(),
                o.getSeller().getId().toString(),
                o.getSeller().getName(),
                o.getBuyer().getId().toString(),
                o.getBuyer().getName(),
                o.getQuantity(),
                o.getAgreedPricePerUnit(),
                o.getTotalAmount(),
                o.getStatus(),
                o.getCreatedAt()
        );
    }
}
