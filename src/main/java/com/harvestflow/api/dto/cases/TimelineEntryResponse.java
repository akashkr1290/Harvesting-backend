package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.TimelineEntry;

import java.time.Instant;

public record TimelineEntryResponse(
        Instant timestamp,
        String actor,
        String action
) {
    public static TimelineEntryResponse from(TimelineEntry entry) {
        return new TimelineEntryResponse(entry.getTimestamp(), entry.getActor(), entry.getAction());
    }
}
