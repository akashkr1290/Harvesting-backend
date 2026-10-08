package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DeliveryOtp;
import com.harvestflow.api.exception.InvalidTransitionException;
import com.harvestflow.api.repository.DeliveryOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Checks the buyer's 6-digit delivery OTP. Runs in its own transaction so a
 * wrong guess is counted even when the caller rolls back; after
 * {@link #MAX_ATTEMPTS} wrong tries the OTP is dead and the buyer must
 * generate a new one. Without this the delivery person could try all
 * one million codes.
 */
@Service
@RequiredArgsConstructor
public class DeliveryOtpVerifier {

    public static final int MAX_ATTEMPTS = 5;

    private final DeliveryOtpRepository otps;
    private final PasswordEncoder encoder;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = InvalidTransitionException.class)
    public void verifyAndConsume(UUID orderId, String otp) {
        DeliveryOtp x = otps.findByMarketOrderId(orderId)
                .orElseThrow(() -> new InvalidTransitionException("Delivery OTP has not been generated."));
        if (x.getConsumedAt() != null || Instant.now().isAfter(x.getExpiresAt())) {
            throw new InvalidTransitionException("Delivery OTP has expired. Ask the buyer to generate a new one.");
        }
        if (x.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new InvalidTransitionException("Too many wrong OTP attempts. Ask the buyer to generate a new one.");
        }
        if (otp == null || !encoder.matches(otp.trim(), x.getOtpHash())) {
            x.setAttemptCount(x.getAttemptCount() + 1);
            otps.save(x);
            int left = MAX_ATTEMPTS - x.getAttemptCount();
            throw new InvalidTransitionException("Wrong delivery OTP. " + Math.max(left, 0) + " attempt(s) left.");
        }
        x.setConsumedAt(Instant.now());
        otps.save(x);
    }
}
