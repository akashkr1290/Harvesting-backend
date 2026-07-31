package com.harvestflow.api.controller;

import com.harvestflow.api.domain.enums.MasterCategory;
import com.harvestflow.api.dto.master.MasterItemRequest;
import com.harvestflow.api.dto.master.MasterItemResponse;
import com.harvestflow.api.service.MasterDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/master-data")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    /**
     * Any authenticated user can read master data — every stage form needs
     * these for its dropdowns. Pass activeOnly=false (Admin's master data
     * screen) to also see deactivated entries.
     */
    @GetMapping("/{category}")
    public List<MasterItemResponse> list(
            @PathVariable MasterCategory category,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return masterDataService.list(category, activeOnly);
    }

    @PostMapping("/{category}")
    @PreAuthorize("hasRole('ADMIN')")
    public MasterItemResponse add(
            @PathVariable MasterCategory category,
            @Valid @RequestBody MasterItemRequest request
    ) {
        return masterDataService.add(category, request);
    }

    @PatchMapping("/{category}/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public MasterItemResponse toggleActive(@PathVariable MasterCategory category, @PathVariable UUID id) {
        return masterDataService.toggleActive(id);
    }

    @DeleteMapping("/{category}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable MasterCategory category, @PathVariable UUID id) {
        masterDataService.delete(id);
    }
}
