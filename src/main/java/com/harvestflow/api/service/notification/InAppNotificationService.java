package com.harvestflow.api.service.notification;

import com.harvestflow.api.domain.entity.Notification;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Default (and, for this phase, only) {@link NotificationService} — persists
 * an in-app {@link Notification} row and logs it. There is no email/SMS/push
 * channel wired in (no such infrastructure — SMTP creds, an SMS gateway,
 * FCM/APNs keys — exists anywhere in this codebase or environment); adding
 * one later is a second implementation of this same interface, not a change
 * to any caller.
 */
@Service
@RequiredArgsConstructor
public class InAppNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationService.class);

    private final NotificationRepository notificationRepository;

    @Override
    public void notify(String recipientUsername, NotificationCategory category, String title, String message,
                        String relatedEntityType, UUID relatedEntityId) {
        Notification notification = new Notification();
        notification.setRecipientUsername(recipientUsername);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        notificationRepository.save(notification);
        log.info("Notification [{}] queued for {}: {}", category, recipientUsername, title);
    }
}
