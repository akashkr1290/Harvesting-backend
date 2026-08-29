package com.harvestflow.api.service.payment;

import java.util.UUID;

/**
 * The single seam every payment attempt flows through, mirroring
 * {@link com.harvestflow.api.service.image.ImageAuthenticityService}'s
 * pattern: {@code PaymentService} depends only on this interface, so
 * swapping the default {@link MockPaymentGatewayService} for a real
 * payment provider later means adding one new {@code @Service} class and
 * flipping {@code harvestflow.payment.provider} in configuration.
 */
public interface PaymentGatewayService {

    /**
     * Attempts to charge/record {@code amount}. Implementations should
     * return {@link PaymentGatewayResult#failed} rather than throwing, so
     * a gateway problem becomes a FAILED payment record instead of a 500.
     */
    PaymentGatewayResult charge(double amount, String currency, UUID referenceId);

    /** Short identifier stored on every PaymentRecord this implementation produces, e.g. "mock-payment-v1". */
    String providerId();
}
