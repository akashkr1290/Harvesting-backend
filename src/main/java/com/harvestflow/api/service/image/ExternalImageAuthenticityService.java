package com.harvestflow.api.service.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Placeholder seam for a real external AI/ML authenticity-detection vendor
 * (e.g. an image-forensics or AI-generation-detection API).
 *
 * <p>No such vendor is integrated in this environment: this sandbox has no
 * outbound network access and no vendor credentials were provided, so
 * wiring an actual HTTP call here would either silently fail or — worse —
 * have to be faked, which this project will not do (see SOW: "Do not
 * invent real-world performance"). This class exists only to make the
 * integration point explicit and typed for whoever adds a real provider.
 *
 * <p><b>To wire a real provider:</b> inject an HTTP client configured with
 * the vendor's base URL/API key (via {@code @Value} or a
 * {@code @ConfigurationProperties} class, following this project's existing
 * JWT-secret pattern in {@code application.yml}), call it in {@link #analyze},
 * map its response onto {@link AuthenticityResult}'s risk-based statuses
 * (never surface a vendor's raw "real"/"fake" boolean directly — translate
 * it into this project's SUSPICIOUS/HIGH_*_RISK vocabulary), and flip
 * {@code harvestflow.image-verification.provider} to {@code external} in
 * the deploying environment's configuration. Keep all vendor-specific
 * request/response shapes inside this one class.
 *
 * <p>Selecting this provider today throws on first use rather than
 * returning a fabricated result — a loud configuration error is preferable
 * to a silent fake verification.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.image-verification.provider", havingValue = "external")
public class ExternalImageAuthenticityService implements ImageAuthenticityService {

    @Override
    public AuthenticityResult analyze(Path imagePath, String contentType) {
        throw new UnsupportedOperationException(
                "harvestflow.image-verification.provider is set to 'external' but no vendor is configured. " +
                        "Wire a real AI provider in ExternalImageAuthenticityService.analyze(), or set the " +
                        "provider back to 'heuristic'.");
    }

    @Override
    public String verifierId() {
        return "external-not-configured";
    }
}
