package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.PaymentRecord;
import com.harvestflow.api.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    List<PaymentRecord> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
            com.harvestflow.api.domain.enums.PaymentReferenceType referenceType, UUID referenceId);

    /** Feeds the SIH analytics "buyer/farmer transaction value" figures — see AnalyticsService. */
    long countByStatus(PaymentStatus status);

    List<PaymentRecord> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
}
