package com.harvestflow.api.dto.master;

import com.harvestflow.api.domain.entity.MasterItem;
import jakarta.validation.constraints.NotBlank;

public record MasterItemRequest(
        @NotBlank String name
) {
}
