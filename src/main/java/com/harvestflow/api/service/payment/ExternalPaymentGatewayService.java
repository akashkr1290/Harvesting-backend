package com.harvestflow.api.service.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Placeholder seam for a real payment gateway (Razorpay, Stripe, UPI, etc.).
 * Not implemented — no network access or gateway credentials in this
 * environment. Selecting this provider throws rather than faking a result;
 * see {@code ExternalImageAuthenticityService} for the identical rationale.
 *
 * <p>To wire a real gateway: inject an HTTP client configured with the
 * provider's API key (following the JWT-secret pattern already in
 * {@code application.yml}), call it in {@link #charge}, map its response
 * onto {@link PaymentGatewayResult}, and flip
 * {@code harvestflow.payment.provider} to {@code external}.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.payment.provider", havingValue = "external")
public class ExternalPaymentGatewayService implements PaymentGatewayService {

    @Override
    public PaymentGatewayResult charge(double amount, String currency, UUID referenceId) {
        throw new UnsupportedOperationException(
                "harvestflow.payment.provider is set to 'external' but no gateway is configured. " +
                        "Wire a real provider in ExternalPaymentGatewayService.charge(), or set the provider back to 'mock'.");
    }

    @Override
    public String providerId() {
        return "external-not-configured";
    }
}
