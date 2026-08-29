package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DeliveryVehicle;
import com.harvestflow.api.dto.logistics.DeliveryVehicleRequest;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.DeliveryVehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Minimal fleet registry backing route optimization's capacity checks. */
@Service
@RequiredArgsConstructor
public class DeliveryVehicleService {

    private final DeliveryVehicleRepository deliveryVehicleRepository;

    @Transactional(readOnly = true)
    public List<DeliveryVehicle> list(boolean activeOnly) {
        return activeOnly
                ? deliveryVehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc()
                : deliveryVehicleRepository.findAllByOrderByRegistrationNumberAsc();
    }

    @Transactional
    public DeliveryVehicle create(DeliveryVehicleRequest request) {
        if (deliveryVehicleRepository.existsByRegistrationNumberIgnoreCase(request.registrationNumber())) {
            throw new IllegalArgumentException(
                    "A vehicle with registration number '%s' already exists.".formatted(request.registrationNumber()));
        }
        DeliveryVehicle vehicle = new DeliveryVehicle();
        vehicle.setRegistrationNumber(request.registrationNumber());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setCapacityKg(request.capacityKg());
        return deliveryVehicleRepository.save(vehicle);
    }

    @Transactional
    public DeliveryVehicle setActive(UUID id, boolean active) {
        DeliveryVehicle vehicle = deliveryVehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        vehicle.setActive(active);
        return deliveryVehicleRepository.save(vehicle);
    }
}
