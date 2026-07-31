package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.HarvestCompletion;

import java.time.LocalDate;

public record CompletionResponse(
        LocalDate actualDate,
        Integer actualBagsOrCrates,
        Double actualWeight,
        String qualityNotes,
        String wastageNotes,
        Double finalVolume,
        Double finalRecovery,
        Double finalPulp,
        String remarks,
        String weightSlipFileName
) {
    public static CompletionResponse from(HarvestCompletion c) {
        if (c == null) return null;
        return new CompletionResponse(
                c.getActualDate(), c.getActualBagsOrCrates(), c.getActualWeight(), c.getQualityNotes(),
                c.getWastageNotes(), c.getFinalVolume(), c.getFinalRecovery(), c.getFinalPulp(),
                c.getRemarks(), c.getWeightSlipFileName()
        );
    }
}
