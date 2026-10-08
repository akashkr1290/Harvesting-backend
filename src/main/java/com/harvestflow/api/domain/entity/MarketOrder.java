package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.OrderStatus;
import com.harvestflow.api.domain.enums.PaymentMethod;
import com.harvestflow.api.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Created the moment a farmer/FPO accepts an offer (Phase 2). Named
 * {@code MarketOrder} (not {@code Order}) to avoid clashing with the SQL
 * keyword and to keep it clearly distinct from any future logistics-owned
 * order concept in Phase 3.
 */
@Entity
@Table(name = "market_order")
@Getter
@Setter
@NoArgsConstructor
public class MarketOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    private Offer offer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ProduceListing listing;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(nullable = false)
    private String cropName;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private Double agreedPricePerUnit;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CONFIRMED;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private Instant deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.harvestflow.api.domain.enums.FulfilmentMode fulfilmentMode =
            com.harvestflow.api.domain.enums.FulfilmentMode.ASSISTED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
