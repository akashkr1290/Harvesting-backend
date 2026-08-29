package com.harvestflow.api.dto.logistics;

import com.harvestflow.api.domain.entity.RouteStop;
import com.harvestflow.api.domain.enums.StopType;

public record RouteStopResponse(
        int sequenceIndex,
        String name,
        double latitude,
        double longitude,
        StopType stopType
) {
    public static RouteStopResponse from(RouteStop s) {
        return new RouteStopResponse(s.getSequenceIndex(), s.getName(), s.getLatitude(), s.getLongitude(), s.getStopType());
    }
}
