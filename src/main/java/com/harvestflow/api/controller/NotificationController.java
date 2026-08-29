package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.Notification;
import com.harvestflow.api.dto.notification.NotificationResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.NotificationRepository;
import com.harvestflow.api.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every authenticated user's own notifications — no role restriction
 * beyond "these are yours": queries are always scoped to the caller's
 * username, so there's nothing to authorize beyond authentication itself.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal SecurityUser principal) {
        return notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(principal.getUsername()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal SecurityUser principal) {
        return Map.of("unreadCount", notificationRepository.countByRecipientUsernameAndReadAtIsNull(principal.getUsername()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal SecurityUser principal) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!notification.getRecipientUsername().equals(principal.getUsername())) {
            throw new ResourceNotFoundException("Notification not found: " + id); // don't leak existence of other users' notifications
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }
}
