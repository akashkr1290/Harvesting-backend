package com.harvestflow.api.domain.enums;

/**
 * Outcome of running a {@link com.harvestflow.api.service.image.ImageAuthenticityService}
 * check against an uploaded produce photo (Phase 4, Part B).
 *
 * Deliberately risk-based, never absolute — the verifier is not able to
 * certify an image as "100% real" or "100% AI-generated", and nothing in
 * this codebase should ever present it that way. Every non-PENDING value
 * below is a claim about *risk*, not a claim of certainty.
 */
public enum ImageVerificationStatus {

    /** Uploaded, not yet run through the authenticity check (or check is queued/in-progress). */
    PENDING,

    /** No manipulation/AI-generation signals were found. Still a likelihood, not a guarantee. */
    AUTHENTICITY_LIKELY,

    /** Some signals present but inconclusive — routed to manual review rather than auto-decided. */
    SUSPICIOUS,

    /** Strong signals that the image has been edited/manipulated after capture. */
    HIGH_MANIPULATION_RISK,

    /** Strong signals that the image is wholly or partly AI-generated/synthetic. */
    HIGH_AI_GENERATION_RISK,

    /** The check itself could not run to completion (corrupt file, unreadable format, checker error). */
    VERIFICATION_FAILED;

    /**
     * Maps a status to the coarse accept/reject/manual-review decision the
     * verification flow (SOW Phase 4, "VERIFICATION FLOW") acts on. Kept as
     * a derived method rather than a stored column so the accept/reject
     * policy can be retuned without a data migration.
     */
    public ImageDecision decision() {
        return switch (this) {
            case AUTHENTICITY_LIKELY -> ImageDecision.ACCEPT;
            case SUSPICIOUS -> ImageDecision.MANUAL_REVIEW;
            case HIGH_MANIPULATION_RISK, HIGH_AI_GENERATION_RISK, VERIFICATION_FAILED -> ImageDecision.REJECT;
            case PENDING -> ImageDecision.MANUAL_REVIEW;
        };
    }
}
