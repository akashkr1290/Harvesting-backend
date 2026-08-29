package com.harvestflow.api.service.payment;

import com.harvestflow.api.domain.enums.PaymentStatus;

/**
 * @param status           SUCCEEDED or FAILED — PENDING is set by the caller before charging, never returned here
 * @param gatewayReference set on SUCCEEDED; a real gateway's transaction id, or (today) a clearly-fake mock one
 * @param failureReason    set on FAILED; a short, user-safe message — never a raw exception message
 */
public record PaymentGatewayResult(PaymentStatus status, String gatewayReference, String failureReason) {
    public static PaymentGatewayResult succeeded(String gatewayReference) {
        return new PaymentGatewayResult(PaymentStatus.SUCCEEDED, gatewayReference, null);
    }

    public static PaymentGatewayResult failed(String reason) {
        return new PaymentGatewayResult(PaymentStatus.FAILED, null, reason);
    }
}
