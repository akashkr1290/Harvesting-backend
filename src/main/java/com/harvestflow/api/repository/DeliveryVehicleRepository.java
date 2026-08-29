package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.DeliveryVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryVehicleRepository extends JpaRepository<DeliveryVehicle, UUID> {

    List<DeliveryVehicle> findByActiveTrueOrderByRegistrationNumberAsc();

    List<DeliveryVehicle> findAllByOrderByRegistrationNumberAsc();

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
}
