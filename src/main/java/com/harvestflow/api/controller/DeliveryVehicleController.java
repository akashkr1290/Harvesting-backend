package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.DeliveryVehicle;
import com.harvestflow.api.dto.logistics.DeliveryVehicleRequest;
import com.harvestflow.api.dto.logistics.DeliveryVehicleResponse;
import com.harvestflow.api.service.DeliveryVehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Fleet registry backing route optimization's vehicle-capacity checks. Managed by ADMIN, like the rest of master data. */
@RestController
@RequestMapping("/api/logistics/vehicles")
@RequiredArgsConstructor
public class DeliveryVehicleController {

    private final DeliveryVehicleService deliveryVehicleService;

    @GetMapping
    public List<DeliveryVehicleResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return deliveryVehicleService.list(activeOnly).stream().map(DeliveryVehicleResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryVehicleResponse create(@Valid @RequestBody DeliveryVehicleRequest request) {
        return DeliveryVehicleResponse.from(deliveryVehicleService.create(request));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryVehicleResponse setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return DeliveryVehicleResponse.from(deliveryVehicleService.setActive(id, active));
    }
}
