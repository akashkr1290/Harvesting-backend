package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record PlotSelectionRequest(
        @NotBlank String location,
        @NotNull LocalDate visitDate,
        String commissionAgentName,
        @NotBlank String farmerName,
        String farmerPhone,
        @NotBlank String village,
        @PositiveOrZero Integer numberOfPlants,
        @NotNull LocalDate harvestingDate,
        String remark,
        @NotNull @PositiveOrZero Double volume,
        @NotNull @PositiveOrZero Double recovery,
        @NotNull @PositiveOrZero Double pulp,
        @NotBlank String selectedCompany,
        String plotCode,
        String gpsNote,
        String priority,
        String photoPath
) {
}
