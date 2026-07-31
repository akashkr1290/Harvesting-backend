package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HarvestCaseRepository extends JpaRepository<HarvestCase, UUID> {

    List<HarvestCase> findByStatusNotOrderByHarvestingDateAsc(CaseStatus excludedStatus);

    List<HarvestCase> findByStatusInOrderByHarvestingDateAsc(List<CaseStatus> statuses);

    /**
     * Supports the Plot Selection search/filter screen (SOW 5.3 D) — search
     * by farmer, village, agent, or company, optionally narrowed by status.
     */
    @Query("""
            SELECT c FROM HarvestCase c
            WHERE (:query IS NULL OR :query = '' OR
                   LOWER(c.farmerName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(c.village) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(c.commissionAgentName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(c.selectedCompany) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.visitDate DESC
            """)
    List<HarvestCase> search(@Param("query") String query, @Param("status") CaseStatus status);

    /** Duplicate prevention: same farmer + location + harvesting date (SOW 5.3 B). */
    boolean existsByFarmerNameIgnoreCaseAndLocationIgnoreCaseAndHarvestingDate(
            String farmerName, String location, java.time.LocalDate harvestingDate);
}
