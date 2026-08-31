package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.domain.enums.PaymentMethod;
import com.harvestflow.api.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A payment attempt against an existing {@code PurchaseInvoice} (farmer
 * payout) or {@code SalesInvoice} (buyer-side sale). Every row processed
 * today went through {@code MockPaymentGatewayService} — see
 * {@code provider} and {@code docs/phase4-integration-notes.md} for what
 * that does and does not mean.
 */
@Entity
@Table(name = "payment_record")
@Getter
@Setter
@NoArgsConstructor
public class PaymentRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentReferenceType referenceType;

    /** id of the PurchaseInvoice or SalesInvoice this payment is against. */
    @Column(nullable = false)
    private UUID referenceId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING) private PaymentMethod paymentMethod;

    /** Which PaymentGatewayService implementation processed this — "mock-payment-v1" today. */
    @Column(nullable = false)
    private String provider;

    /** Set on SUCCEEDED — a fake reference from the mock gateway, never a real transaction id. */
    private String gatewayReference;

    @Column(length=1000) private String paymentUrl;

    private String failureReason;

    @Column(nullable = false)
    private String initiatedByUsername;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;
}
