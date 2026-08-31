package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.DeliveryAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
    Optional<DeliveryAssignment> findByMarketOrderId(UUID marketOrderId);
    java.util.List<DeliveryAssignment> findByDeliveryBoyUsernameOrderByAssignedAtDesc(String username);
}
