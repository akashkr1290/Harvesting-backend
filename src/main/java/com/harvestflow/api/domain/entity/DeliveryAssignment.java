package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.DeliveryStatus;
import com.harvestflow.api.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignment")
@Getter @Setter @NoArgsConstructor
public class DeliveryAssignment {
    @Id @GeneratedValue private UUID id;
    @OneToOne(optional=false) @JoinColumn(name="market_order_id", nullable=false, unique=true) private MarketOrder marketOrder;
    @Column(nullable=false) private String deliveryBoyUsername;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private DeliveryStatus status = DeliveryStatus.ASSIGNED;
    @Enumerated(EnumType.STRING) private PaymentMethod paymentMethod;
    @Column(nullable=false) private boolean pickupConfirmed = false;
    @Column(nullable=false) private boolean buyerReached = false;
    private Instant assignedAt = Instant.now();
    private Instant pickedUpAt;
    private Instant buyerReachedAt;
    private Instant completedAt;
    @Column(length=500) private String pickupNote;
}
