package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.PackingMaterialLine;
import com.harvestflow.api.domain.entity.PackingMaterialRecord;
import com.harvestflow.api.domain.enums.PackingStage;

import java.time.Instant;
import java.util.List;

public record PackingRecordResponse(
        PackingStage stage,
        Instant recordedAt,
        String handledBy,
        String remarks,
        List<LineDto> lines
) {
    public record LineDto(String itemName, Double quantity, String unit) {
    }

    public static PackingRecordResponse from(PackingMaterialRecord record) {
        if (record == null) return null;
        List<LineDto> lines = record.getLines().stream()
                .map(PackingRecordResponse::toLineDto)
                .toList();
        return new PackingRecordResponse(
                record.getStage(), record.getRecordedAt(), record.getHandledBy(), record.getRemarks(), lines
        );
    }

    private static LineDto toLineDto(PackingMaterialLine line) {
        return new LineDto(line.getItemName(), line.getQuantity(), line.getUnit());
    }
}
