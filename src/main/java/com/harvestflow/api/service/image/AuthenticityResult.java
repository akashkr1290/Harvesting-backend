package com.harvestflow.api.service.image;

import com.harvestflow.api.domain.enums.ImageVerificationStatus;

import java.util.List;

/**
 * The output contract every {@link ImageAuthenticityService} implementation
 * must return, regardless of what's running underneath (rule-based
 * heuristics today, a trained model or a vendor API in a future phase).
 *
 * @param status      risk-based verdict — never treat as a certainty
 * @param confidence  0.0-1.0, this checker's confidence in its own verdict;
 *                    {@code null} when the checker declines to quantify one
 *                    (e.g. on VERIFICATION_FAILED)
 * @param reasons     short, human-readable signals that led to the verdict
 *                    (shown to reviewers / logged) — never raw model
 *                    internals or a fabricated-sounding score breakdown
 * @param verifierId  identifies which checker produced this result, e.g.
 *                    "heuristic-v1"; stored alongside the result so a mixed
 *                    history (heuristic now, vendor API later) stays legible
 */
public record AuthenticityResult(
        ImageVerificationStatus status,
        Double confidence,
        List<String> reasons,
        String verifierId
) {
    public static AuthenticityResult failed(String verifierId, String reason) {
        return new AuthenticityResult(ImageVerificationStatus.VERIFICATION_FAILED, null, List.of(reason), verifierId);
    }
}
