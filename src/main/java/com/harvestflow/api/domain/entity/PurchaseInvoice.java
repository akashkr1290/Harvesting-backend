package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_invoice")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoice {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private HarvestCase harvestCase;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private Double rate;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private boolean includeCommission = true;
    private Double commissionAmount = 0.0;

    @Column(nullable = false)
    private boolean includePackingCost = true;
    private Double packingCost = 0.0;

    @Column(nullable = false)
    private boolean includeTransportCost = true;
    private Double transportCost = 0.0;

    private Double otherCharges = 0.0;
    private Double deductions = 0.0;
    private Double additions = 0.0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public double getBaseAmount() {
        return rate * weight;
    }

    /** Net payable to farmer — admin decides which components apply
     * (Working Approach doc, section 5). Computed on read, not stored,
     * so it always reflects the current component values. */
    @Transient
    public double getNetPayable() {
        double net = getBaseAmount();
        if (includeCommission) net -= nz(commissionAmount);
        if (includePackingCost) net -= nz(packingCost);
        if (includeTransportCost) net -= nz(transportCost);
        net -= nz(otherCharges);
        net -= nz(deductions);
        net += nz(additions);
        return net;
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }
}
