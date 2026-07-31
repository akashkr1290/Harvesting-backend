package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.PlanningDetails;

import java.time.LocalDate;

public record PlanningResponse(
        LocalDate plannedDate,
        String timeWindow,
        String supervisorName,
        String pickupPersonName,
        String vehiclePlan,
        Double expectedQuantity,
        String notesForGodown,
        String notesForSupervisor
) {
    public static PlanningResponse from(PlanningDetails p) {
        if (p == null) return null;
        return new PlanningResponse(
                p.getPlannedDate(), p.getTimeWindow(), p.getSupervisorName(), p.getPickupPersonName(),
                p.getVehiclePlan(), p.getExpectedQuantity(), p.getNotesForGodown(), p.getNotesForSupervisor()
        );
    }
}
