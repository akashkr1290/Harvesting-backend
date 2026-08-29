package com.harvestflow.api.domain.enums;

/**
 * Coarse outcome the verification flow and the marketplace-facing frontend
 * act on. {@link ImageVerificationStatus} carries the detailed reason;
 * this is the three-way switch everything downstream actually branches on.
 */
public enum ImageDecision {
    ACCEPT,
    MANUAL_REVIEW,
    REJECT
}
