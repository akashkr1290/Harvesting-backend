package com.harvestflow.api.dto.logistics;

import com.harvestflow.api.domain.entity.DeliveryVehicle;

public record DeliveryVehicleResponse(
        String id,
        String registrationNumber,
        String vehicleType,
        double capacityKg,
        boolean active
) {
    public static DeliveryVehicleResponse from(DeliveryVehicle v) {
        return new DeliveryVehicleResponse(
                v.getId().toString(), v.getRegistrationNumber(), v.getVehicleType(), v.getCapacityKg(), v.isActive());
    }
}
