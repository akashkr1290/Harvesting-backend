package com.harvestflow.api.service.image;

import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Deterministic, no-op stand-in for {@link ImageAuthenticityService}.
 *
 * <p>This exists solely so local development and automated tests can run
 * the full upload → verify → decision pipeline without decoding real image
 * bytes or depending on {@link HeuristicImageAuthenticityService}'s file
 * parsing. It performs <b>no actual authenticity analysis whatsoever</b>.
 *
 * <p>Every result this returns is tagged with verifier id
 * {@code "mock-dev-not-real-verification"} and every reason string starts
 * with an explicit disclaimer, specifically so a result from this provider
 * can never be mistaken for — or silently logged/displayed as — a real
 * authenticity determination. This class also logs a warning on every
 * use (see {@link #analyze}) so a mock-verified image reaching a
 * farmer-facing screen shows up loudly in logs, not silently.
 *
 * <p>Activate with {@code harvestflow.image-verification.provider: mock}.
 * Never set this in a prod profile — {@code application-prod.yml} should
 * always pin this to {@code heuristic} (or a real provider once one exists).
 */
@Service
@ConditionalOnProperty(name = "harvestflow.image-verification.provider", havingValue = "mock")
public class MockImageAuthenticityService implements ImageAuthenticityService {

    private static final Logger log = LoggerFactory.getLogger(MockImageAuthenticityService.class);
    private static final String VERIFIER_ID = "mock-dev-not-real-verification";
    private static final String DISCLAIMER =
            "MOCK RESULT — no real authenticity analysis was performed. This provider exists for local development/testing only.";

    @Override
    public AuthenticityResult analyze(Path imagePath, String contentType) {
        log.warn("MockImageAuthenticityService used for {} — this is NOT a real authenticity check. " +
                "Do not enable this provider in any environment shown to real users.", imagePath.getFileName());
        return new AuthenticityResult(
                ImageVerificationStatus.AUTHENTICITY_LIKELY,
                null, // no confidence — nothing was actually measured
                List.of(DISCLAIMER),
                VERIFIER_ID
        );
    }

    @Override
    public String verifierId() {
        return VERIFIER_ID;
    }
}
