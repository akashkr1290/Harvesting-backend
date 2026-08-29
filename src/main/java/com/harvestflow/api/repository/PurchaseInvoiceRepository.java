package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Did not exist before Phase 4 — PurchaseInvoice was previously only ever
 * reached via HarvestCase's cascade relationship. Added so PaymentService
 * can validate a payment's referenceId without touching HarvestCase at all.
 */
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID> {
}
