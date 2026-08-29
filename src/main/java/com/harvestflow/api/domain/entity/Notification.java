package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An in-app notification for one user. Deliberately generic — not tied to
 * image verification or payments specifically — so any later phase
 * (marketplace order updates, etc.) can reuse {@code NotificationService}
 * without a new entity.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String recipientUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    /** e.g. "PRODUCE_IMAGE" / "PAYMENT_RECORD" — loosely typed on purpose, this is a notification log, not a foreign key. */
    private String relatedEntityType;

    private UUID relatedEntityId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant readAt;
}
