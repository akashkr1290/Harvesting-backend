package com.harvestflow.api.domain.enums;

/** Outcome of a (currently always mock, see MockPaymentGatewayService) payment attempt. */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
