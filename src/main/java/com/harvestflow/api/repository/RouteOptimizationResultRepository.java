package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.RouteOptimizationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteOptimizationResultRepository extends JpaRepository<RouteOptimizationResult, UUID> {

    List<RouteOptimizationResult> findByHarvestCase_IdOrderByCreatedAtDesc(UUID caseId);

    /** Phase 5: market-order-scoped equivalent, added once RouteOptimizationResult.marketOrder existed. */
    List<RouteOptimizationResult> findByMarketOrder_IdOrderByCreatedAtDesc(UUID marketOrderId);
}
