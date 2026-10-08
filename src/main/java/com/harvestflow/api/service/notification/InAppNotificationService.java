package com.harvestflow.api.service.notification;

import com.harvestflow.api.domain.entity.Notification;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.repository.NotificationRepository;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.service.sms.SmsSender;
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
    private final UserRepository userRepository;
    private final SmsSender smsSender;

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
        forwardBySms(recipientUsername, category, title, message);
    }

    /**
     * Farmers often do not open the app, so offer, payment and delivery
     * updates also go out by SMS when a real SMS gateway is configured.
     * A failed SMS never breaks the business action that triggered it.
     */
    private void forwardBySms(String recipientUsername, NotificationCategory category, String title, String message) {
        if (!smsSender.deliversToPhone() || category == NotificationCategory.IMAGE_VERIFICATION || recipientUsername == null) {
            return;
        }
        try {
            userRepository.findByUsername(recipientUsername)
                    .map(u -> u.getMobile())
                    .filter(m -> m != null && m.matches("\\d{10}"))
                    .ifPresent(mobile -> {
                        String text = "HarvestFlow: " + title + ". " + (message == null ? "" : message);
                        smsSender.send(mobile, text.length() > 300 ? text.substring(0, 297) + "..." : text);
                    });
        } catch (Exception e) {
            log.warn("SMS forwarding failed for {}: {}", recipientUsername, e.getMessage());
        }
    }
}
