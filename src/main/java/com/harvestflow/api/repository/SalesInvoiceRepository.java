package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Did not exist before Phase 4 — SalesInvoice was previously only ever
 * reached via HarvestCase's cascade relationship. Added so PaymentService
 * and AnalyticsService can query it directly without touching HarvestCase.
 */
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
}
