package com.harvestflow.registration.service.impl;

import com.harvestflow.api.service.sms.SmsSender;
import com.harvestflow.registration.entity.OtpChallenge;
import com.harvestflow.registration.entity.OtpPurpose;
import com.harvestflow.registration.exception.OtpAlreadyConsumedException;
import com.harvestflow.registration.exception.OtpExpiredException;
import com.harvestflow.registration.exception.OtpInvalidException;
import com.harvestflow.registration.exception.RegistrationException;
import com.harvestflow.registration.exception.TooManyOtpAttemptsException;
import com.harvestflow.registration.repository.OtpChallengeRepository;
import com.harvestflow.registration.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Registration OTPs. The OTP is sent by {@link SmsSender}.
 *
 * <p>The old code accepted "123456" for every account in every environment.
 * That shortcut now exists only when {@code harvestflow.otp.dev-mode=true}
 * (default true in the local dev profile, false in application-prod.yml),
 * and in dev mode the real OTP is also written to the log so testers can
 * use it. Never enable dev mode on a server real users can reach.
 */
@Service
public class DefaultOtpService implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOtpService.class);
    static final String DEV_OTP = "123456";

    private final OtpChallengeRepository repo;
    private final PasswordEncoder encoder;
    private final SmsSender sms;
    private final boolean devMode;
    private final SecureRandom random = new SecureRandom();

    public DefaultOtpService(OtpChallengeRepository repo, PasswordEncoder encoder, SmsSender sms,
                             @Value("${harvestflow.otp.dev-mode:false}") boolean devMode) {
        this.repo = repo;
        this.encoder = encoder;
        this.sms = sms;
        this.devMode = devMode;
        if (devMode) {
            log.warn("OTP dev mode is ON: the code {} is accepted for every registration. Turn it off on public servers (OTP_DEV_MODE=false).", DEV_OTP);
        }
    }

    @Override
    public void sendOtp(UUID id, OtpPurpose purpose, String destination) {
        repo.findTopByRegistrationSessionIdAndPurposeOrderByCreatedAtDesc(id, purpose)
                .filter(x -> x.getConsumedAt() == null)
                .ifPresent(x -> {
                    if (Instant.now().isBefore(x.getCreatedAt().plusSeconds(30))) {
                        throw new RegistrationException("Please wait before requesting another OTP");
                    }
                });
        String otp = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge c = new OtpChallenge();
        c.setRegistrationSessionId(id);
        c.setPurpose(purpose);
        c.setOtpHash(encoder.encode(otp));
        c.setExpiresAt(Instant.now().plusSeconds(300));
        repo.save(c);

        boolean sent = sms.send(destination, "HarvestFlow OTP: " + otp + ". Valid for 5 minutes. Do not share it.");
        if (devMode) {
            log.info("[OTP dev mode] {} OTP for session {}: {}", purpose, id, otp);
        } else if (!sent || !sms.deliversToPhone()) {
            log.error("OTP could not be delivered by SMS (provider sent={}, deliversToPhone={}). Configure SMS_PROVIDER=http.",
                    sent, sms.deliversToPhone());
        }
    }

    /**
     * Runs in its own transaction so a wrong guess is counted even though the
     * caller's transaction rolls back on the exception; otherwise the
     * five-attempt limit could never trigger.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = OtpInvalidException.class)
    public void verifyOtp(UUID id, OtpPurpose purpose, String otp) {
        OtpChallenge c = repo.findTopByRegistrationSessionIdAndPurposeOrderByCreatedAtDesc(id, purpose)
                .orElseThrow(() -> new OtpInvalidException("No OTP was requested"));
        if (c.getConsumedAt() != null) throw new OtpAlreadyConsumedException("OTP already used");
        if (Instant.now().isAfter(c.getExpiresAt())) throw new OtpExpiredException("OTP expired");
        if (c.getAttemptCount() >= 5) throw new TooManyOtpAttemptsException("Too many incorrect attempts, request a new OTP");
        c.setAttemptCount(c.getAttemptCount() + 1);
        boolean ok = encoder.matches(otp, c.getOtpHash()) || (devMode && DEV_OTP.equals(otp));
        if (!ok) {
            repo.save(c);
            throw new OtpInvalidException("Incorrect OTP");
        }
        c.setConsumedAt(Instant.now());
        repo.save(c);
    }
}
