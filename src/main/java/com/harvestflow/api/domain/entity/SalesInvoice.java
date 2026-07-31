package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_invoice")
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoice {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private HarvestCase harvestCase;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private String buyerCompany;

    @Column(nullable = false)
    private Double salesRate;

    @Column(nullable = false)
    private Double weight;

    private Double taxPercent = 0.0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Transient
    public double getSubtotal() {
        return salesRate * weight;
    }

    @Transient
    public double getTaxAmount() {
        return getSubtotal() * ((taxPercent == null ? 0.0 : taxPercent) / 100.0);
    }

    @Transient
    public double getTotal() {
        return getSubtotal() + getTaxAmount();
    }
}
