package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.OfferStatus;
import com.harvestflow.api.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A buyer's offer against a {@link ProduceListing} (Phase 2). */
@Entity
@Table(name = "marketplace_offer")
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ProduceListing listing;

    @ManyToOne(optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole buyerRole;

    @Column(nullable = false)
    private String buyerName;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private Double pricePerUnit;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status = OfferStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant decidedAt;

    /** Pending offers stop blocking the farmer after this time; see OfferExpiryJob. */
    private Instant expiresAt;
}
