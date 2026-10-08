package com.harvestflow.api.dto.marketplace;

import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.enums.CaseStatus;
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
        Instant createdAt,
        String expectedHarvestDate,
        String harvestCaseId,
        CaseStatus operationalStatus,
        Instant deliveredAt,
        com.harvestflow.api.domain.enums.PaymentMethod paymentMethod,
        com.harvestflow.api.domain.enums.PaymentStatus paymentStatus,
        com.harvestflow.api.domain.enums.FulfilmentMode fulfilmentMode
) {
    public static OrderResponse from(MarketOrder o) {
        return from(o, null);
    }

    public static OrderResponse from(MarketOrder o, com.harvestflow.api.domain.entity.HarvestCase harvestCase) {
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
                o.getCreatedAt(),
                o.getListing().getHarvestDate() == null ? null : o.getListing().getHarvestDate().toString(),
                harvestCase == null ? null : harvestCase.getId().toString(),
                harvestCase == null ? null : harvestCase.getStatus(),
                o.getDeliveredAt(),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                o.getFulfilmentMode()
        );
    }
}
