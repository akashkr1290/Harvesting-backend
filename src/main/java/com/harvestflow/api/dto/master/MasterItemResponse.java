package com.harvestflow.api.dto.master;

import com.harvestflow.api.domain.entity.MasterItem;
import com.harvestflow.api.domain.enums.MasterCategory;

public record MasterItemResponse(
        String id,
        MasterCategory category,
        String name,
        boolean active
) {
    public static MasterItemResponse from(MasterItem item) {
        return new MasterItemResponse(item.getId().toString(), item.getCategory(), item.getName(), item.isActive());
    }
}
