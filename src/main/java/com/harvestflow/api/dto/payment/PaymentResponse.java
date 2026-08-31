package com.harvestflow.api.dto.payment;

import com.harvestflow.api.domain.entity.PaymentRecord;
import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.domain.enums.PaymentMethod;
import com.harvestflow.api.domain.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        PaymentReferenceType referenceType,
        UUID referenceId,
        double amount,
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        String provider,
        String gatewayReference,
        String paymentUrl,
        String failureReason,
        String note,
        Instant createdAt,
        Instant completedAt
) {
    public static PaymentResponse from(PaymentRecord p) {
        return new PaymentResponse(p.getId(), p.getReferenceType(), p.getReferenceId(), p.getAmount(),
                p.getCurrency(), p.getStatus(), p.getPaymentMethod(), p.getProvider(), p.getGatewayReference(), p.getPaymentUrl(), p.getFailureReason(),
                p.getNote(), p.getCreatedAt(), p.getCompletedAt());
    }
}
