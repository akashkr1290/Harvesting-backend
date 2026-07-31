package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlanningRequest(
        @NotNull LocalDate plannedDate,
        String timeWindow,
        @NotBlank String supervisorName,
        @NotBlank String pickupPersonName,
        String vehiclePlan,
        Double expectedQuantity,
        String notesForGodown,
        String notesForSupervisor
) {
}
