package com.harvestflow.api.service.payment;
import java.util.UUID;
public interface PaymentGatewayService {
    PaymentGatewayResult charge(double amount, String currency, UUID referenceId);
    default PaymentGatewayResult createPaymentIntent(double amount, String currency, UUID referenceId) {
        return PaymentGatewayResult.failed("Digital payment gateway is not configured.");
    }
    default boolean verifyWebhook(String gatewayReference, String expectedOrderReference, String payload, String signature) { return false; }
    String providerId();
}
