package com.harvestflow.api.service.payment;

import com.harvestflow.api.domain.enums.PaymentStatus;

public record PaymentGatewayResult(PaymentStatus status, String gatewayReference, String failureReason, String paymentUrl) {
    public static PaymentGatewayResult succeeded(String reference) { return new PaymentGatewayResult(PaymentStatus.SUCCEEDED, reference, null, null); }
    public static PaymentGatewayResult initiated(String reference, String paymentUrl) { return new PaymentGatewayResult(PaymentStatus.PENDING, reference, null, paymentUrl); }
    public static PaymentGatewayResult failed(String reason) { return new PaymentGatewayResult(PaymentStatus.FAILED, null, reason, null); }
}
