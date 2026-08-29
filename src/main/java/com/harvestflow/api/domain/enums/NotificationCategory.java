package com.harvestflow.api.domain.enums;

/** What a Notification is about — kept small and generic so any future phase can add its own trigger without a new enum. */
public enum NotificationCategory {
    IMAGE_VERIFICATION,
    PAYMENT,
    /** Phase 5: offer submitted/accepted/rejected on a marketplace listing. Added once OfferService could notify. */
    OFFER,
    GENERAL
}
