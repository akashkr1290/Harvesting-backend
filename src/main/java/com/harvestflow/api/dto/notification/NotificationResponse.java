package com.harvestflow.api.dto.notification;

import com.harvestflow.api.domain.entity.Notification;
import com.harvestflow.api.domain.enums.NotificationCategory;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationCategory category,
        String title,
        String message,
        String relatedEntityType,
        UUID relatedEntityId,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getCategory(), n.getTitle(), n.getMessage(),
                n.getRelatedEntityType(), n.getRelatedEntityId(), n.getCreatedAt(), n.getReadAt());
    }
}
