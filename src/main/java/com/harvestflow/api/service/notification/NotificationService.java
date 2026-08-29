package com.harvestflow.api.service.notification;

import com.harvestflow.api.domain.enums.NotificationCategory;

import java.util.UUID;

/**
 * The one seam anything in this codebase should go through to notify a
 * user of something — Phase 4 wires it up from image verification and
 * payment events, but it is deliberately not specific to either. A future
 * phase (marketplace order updates, etc.) reuses this interface rather
 * than inventing its own notification path.
 */
public interface NotificationService {

    void notify(String recipientUsername, NotificationCategory category, String title, String message,
                String relatedEntityType, UUID relatedEntityId);
}
