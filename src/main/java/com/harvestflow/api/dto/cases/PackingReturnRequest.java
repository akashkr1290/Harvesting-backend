package com.harvestflow.api.dto.cases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PackingReturnRequest(
        String remarks,
        @NotEmpty @Valid List<PackingLineDto> lines
) {
}
