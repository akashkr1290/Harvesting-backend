package com.harvestflow.api.domain.enums;

/** Lifecycle of a buyer's offer against a listing (Phase 2). */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    /** Pending past its expiry time without a decision. */
    EXPIRED
}
