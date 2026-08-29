package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.DemandPrediction;
import com.harvestflow.api.dto.demand.DemandPredictionRequest;
import com.harvestflow.api.dto.demand.DemandPredictionResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.DemandForecastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Part A — AI demand forecasting API. Generating a forecast is restricted
 * to the roles that plan ahead of a season (ADMIN, PLANNING); reading a
 * forecast is open to any authenticated user, since it's informational
 * and carries no farmer financial data (unlike, say, purchase rates).
 */
@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
public class DemandForecastController {

    private final DemandForecastService demandForecastService;

    /** Generate (and store) a new forecast run for one crop/location. */
    @PostMapping("/predict")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNING')")
    @ResponseStatus(HttpStatus.CREATED)
    public DemandPredictionResponse predict(
            @Valid @RequestBody DemandPredictionRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        DemandPrediction prediction = demandForecastService.generatePrediction(
                request.cropType(), request.location(), principal.getUser().getUsername());
        return DemandPredictionResponse.from(prediction);
    }

    /** Most recent forecast for a crop/location — what the dashboard shows by default. */
    @GetMapping("/latest")
    public DemandPredictionResponse latest(@RequestParam String cropType, @RequestParam String location) {
        return DemandPredictionResponse.from(demandForecastService.getLatest(cropType, location));
    }

    /** Forecast run history for a crop/location, most recent first — drives the trend view. */
    @GetMapping("/history")
    public List<DemandPredictionResponse> history(@RequestParam String cropType, @RequestParam String location) {
        return demandForecastService.getHistory(cropType, location).stream()
                .map(DemandPredictionResponse::from).toList();
    }

    /** Latest forecast across every crop/location that has one — the marketplace-facing overview list. */
    @GetMapping
    public List<DemandPredictionResponse> all() {
        return demandForecastService.getAllLatest().stream().map(DemandPredictionResponse::from).toList();
    }
}
