package com.harvestflow.api.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Deterministic, no-real-money stand-in for {@link PaymentGatewayService}.
 *
 * <p>No payment gateway is integrated in this environment — this sandbox
 * has no outbound network access and no gateway credentials were provided,
 * so, exactly as with {@code MockImageAuthenticityService}, this project
 * will not fake a real integration. Every result is tagged with provider
 * id {@code "mock-payment-v1"} and every gateway reference is prefixed
 * {@code MOCK-} so it can never be mistaken for a real transaction id in
 * logs, the database, or the UI.
 *
 * <p>Rule: any positive amount succeeds; zero or negative fails. This is
 * enough to exercise both branches of the payment flow (and every UI state
 * that depends on them) without pretending to model real payment failure
 * modes (insufficient funds, card declines, etc.) that would be dishonest
 * to simulate without a real gateway behind them.
 *
 * <p>Activate with {@code harvestflow.payment.provider: mock} (the
 * default). Never treat a SUCCEEDED result from this provider as proof
 * money actually moved.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGatewayService implements PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayService.class);
    private static final String PROVIDER_ID = "mock-payment-v1";

    @Override
    public PaymentGatewayResult charge(double amount, String currency, UUID referenceId) {
        log.info("MockPaymentGatewayService processing a SIMULATED charge of {} {} for reference {} — " +
                "no real payment gateway is integrated; no money moves.", amount, currency, referenceId);
        if (amount <= 0) {
            return PaymentGatewayResult.failed("Amount must be greater than zero.");
        }
        return PaymentGatewayResult.succeeded("MOCK-" + UUID.randomUUID());
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }
}
