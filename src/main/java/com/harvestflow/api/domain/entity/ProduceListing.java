package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A farmer/FPO produce listing on the marketplace (Phase 2). Denormalizes
 * seller name/role onto the listing itself so buyer-facing screens never
 * need a second lookup just to render a card — the same pattern
 * {@code HarvestCase} uses for farmer name/village.
 */
@Entity
@Table(name = "produce_listing")
@Getter
@Setter
@NoArgsConstructor
public class ProduceListing {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** Snapshot at creation time — FARMER or FPO. Kept even if the user's role is ever changed later. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole sellerRole;

    @Column(nullable = false)
    private String sellerName;

    @Column(nullable = false)
    private String cropName;

    @Column(nullable = false)
    private Double quantity;

    /**
     * Quantity still for sale. Starts equal to {@link #quantity}; every
     * accepted offer subtracts from it, and the listing closes only when it
     * reaches zero. Before this, one small offer closed the whole lot.
     */
    @Column(nullable = false)
    private Double availableQuantity;

    @Column(nullable = false)
    private String unit;

    private String quality;

    @Column(nullable = false)
    private LocalDate harvestDate;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Double expectedPricePerUnit;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.DRAFT;

    /**
     * Image URLs already uploaded for this listing (Phase 2 UI foundation
     * only — see {@link #primaryImageIndex} and {@link #verificationStatus}).
     * The AI authenticity check itself is Phase 4's responsibility; this
     * field just displays whatever verificationStatus the backend is given.
     */
    @ElementCollection
    @CollectionTable(name = "produce_listing_images", joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "image_url")
    @OrderColumn(name = "position")
    private List<String> imageUrls = new ArrayList<>();

    /** Index into {@link #imageUrls} of the primary/cover photo, or null if none set. */
    private Integer primaryImageIndex;

    /**
     * Placeholder for Phase 4's authenticity verification result
     * ("NOT_SUBMITTED", "PENDING", "VERIFIED", "REJECTED"). Phase 2 never
     * writes anything here beyond the default — it only renders it.
     */
    @Column(nullable = false)
    private String verificationStatus = "NOT_SUBMITTED";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant publishedAt;

    private Instant closedAt;

    @PrePersist
    void defaultAvailableQuantity() {
        if (availableQuantity == null) {
            availableQuantity = quantity;
        }
    }
}
