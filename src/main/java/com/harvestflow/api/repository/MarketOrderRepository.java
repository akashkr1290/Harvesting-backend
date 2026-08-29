package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.MarketOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, UUID> {

    List<MarketOrder> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    List<MarketOrder> findByBuyer_IdOrderByCreatedAtDesc(UUID buyerId);
}
