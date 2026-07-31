package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record HarvestCompletionRequest(
        @NotNull LocalDate actualDate,
        Integer actualBagsOrCrates,
        @NotNull @Positive Double actualWeight,
        String qualityNotes,
        String wastageNotes,
        Double finalVolume,
        Double finalRecovery,
        Double finalPulp,
        String remarks,
        String weightSlipFileName
) {
}
