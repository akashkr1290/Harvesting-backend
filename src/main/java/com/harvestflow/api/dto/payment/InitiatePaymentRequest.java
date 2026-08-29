package com.harvestflow.api.dto.payment;

import com.harvestflow.api.domain.enums.PaymentReferenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiatePaymentRequest(
        @NotNull PaymentReferenceType referenceType,
        @NotNull java.util.UUID referenceId,
        @NotNull @Positive Double amount,
        String currency,
        String note
) {
}
