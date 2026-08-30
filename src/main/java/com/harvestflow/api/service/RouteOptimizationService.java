package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DeliveryVehicle;
import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.entity.MasterItem;
import com.harvestflow.api.domain.entity.RouteOptimizationResult;
import com.harvestflow.api.domain.entity.RouteStop;
import com.harvestflow.api.domain.enums.RouteStatus;
import com.harvestflow.api.domain.enums.StopType;
import com.harvestflow.api.dto.logistics.RouteOptimizeRequest;
import com.harvestflow.api.dto.logistics.RouteStopRequest;
import com.harvestflow.api.exception.InvalidLocationException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.exception.RouteImpossibleException;
import com.harvestflow.api.exception.VehicleCapacityExceededException;
import com.harvestflow.api.repository.DeliveryVehicleRepository;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.repository.MasterItemRepository;
import com.harvestflow.api.repository.RouteOptimizationResultRepository;
import com.harvestflow.api.service.routing.RouteOptimizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Part B (route optimization) + Part C (marketplace-to-logistics
 * integration point). Optimizing a route is synchronous and stateless
 * over its inputs; the only persistence side effect beyond saving the
 * result is an optional timeline entry on the linked {@link HarvestCase},
 * which is what makes this "integration" rather than a bare calculator —
 * see the {@code caseId} handling below. {@code marketOrderId} (Phase 5)
 * is the equivalent link for a real marketplace order.
 */
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    /** Average effective road speed used to turn distance into a duration estimate — a deliberately simple MVP proxy, not a routing-API ETA. */
    private static final double ASSUMED_AVERAGE_SPEED_KMH = 35.0;

    /** Minutes added per stop for loading/unloading. */
    private static final double MINUTES_PER_STOP = 15.0;

    private final RouteOptimizer routeOptimizer;
    private final RouteOptimizationResultRepository routeOptimizationResultRepository;
    private final DeliveryVehicleRepository deliveryVehicleRepository;
    private final HarvestCaseRepository harvestCaseRepository;
    private final MasterItemRepository masterItemRepository;
    private final MarketOrderRepository marketOrderRepository;

    @Transactional
    public RouteOptimizationResult optimize(RouteOptimizeRequest request, String username) {
        DeliveryVehicle vehicle = null;
        if (request.vehicleId() != null) {
            vehicle = deliveryVehicleRepository.findById(request.vehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + request.vehicleId()));
            if (request.totalQuantityKg() > vehicle.getCapacityKg()) {
                throw new VehicleCapacityExceededException(
                        "Load of %.1f kg exceeds vehicle %s's capacity of %.1f kg."
                                .formatted(request.totalQuantityKg(), vehicle.getRegistrationNumber(), vehicle.getCapacityKg()));
            }
        }

        HarvestCase harvestCase = null;
        if (request.caseId() != null) {
            harvestCase = harvestCaseRepository.findById(request.caseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + request.caseId()));
        }

        MarketOrder marketOrder = null;
        if (request.marketOrderId() != null) {
            marketOrder = marketOrderRepository.findById(request.marketOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.marketOrderId()));
        } else if (harvestCase != null && harvestCase.getMarketOrderId() != null) {
            final HarvestCase resolvedCase = harvestCase;
            marketOrder = marketOrderRepository.findById(resolvedCase.getMarketOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Marketplace order not found: " + resolvedCase.getMarketOrderId()));
        }

        List<RouteOptimizer.Point> inputStops = new ArrayList<>();
        inputStops.add(resolveStop(request.pickup()));
        for (RouteStopRequest d : request.deliveries()) {
            inputStops.add(resolveStop(d));
        }

        double originalDistanceKm;
        RouteOptimizer.Result optimized;
        try {
            originalDistanceKm = routeOptimizer.totalDistanceInGivenOrder(inputStops);
            optimized = routeOptimizer.optimize(inputStops);
        } catch (RuntimeException ex) {
            throw new RouteImpossibleException("Could not compute a route for the given stops: " + ex.getMessage());
        }

        if (optimized.orderedStops().isEmpty() || Double.isNaN(optimized.totalDistanceKm())) {
            throw new RouteImpossibleException("Could not compute a route for the given stops.");
        }

        double distanceSaved = Math.max(0.0, originalDistanceKm - optimized.totalDistanceKm());
        double durationMinutes = (optimized.totalDistanceKm() / ASSUMED_AVERAGE_SPEED_KMH) * 60.0
                + MINUTES_PER_STOP * (optimized.orderedStops().size() - 1);
        double estimatedCost = optimized.totalDistanceKm() * request.costPerKm();

        RouteOptimizationResult result = new RouteOptimizationResult();
        result.setHarvestCase(harvestCase);
        result.setMarketOrder(marketOrder);
        result.setVehicle(vehicle);
        result.setStatus(vehicle != null ? RouteStatus.ASSIGNED : RouteStatus.OPTIMIZED);
        result.setTotalQuantityKg(request.totalQuantityKg());
        result.setOriginalDistanceKm(round(originalDistanceKm));
        result.setOptimizedDistanceKm(round(optimized.totalDistanceKm()));
        result.setDistanceSavedKm(round(distanceSaved));
        result.setEstimatedDurationMinutes(round(durationMinutes));
        result.setEstimatedCost(round(estimatedCost));
        result.setRequestedByUsername(username);

        List<RouteOptimizer.Point> ordered = optimized.orderedStops();
        for (int i = 0; i < ordered.size(); i++) {
            RouteStop stop = new RouteStop();
            stop.setRouteResult(result);
            stop.setSequenceIndex(i);
            stop.setName(ordered.get(i).name());
            stop.setLatitude(ordered.get(i).lat());
            stop.setLongitude(ordered.get(i).lng());
            stop.setStopType(i == 0 ? StopType.PICKUP : StopType.DELIVERY);
            result.getStops().add(stop);
        }

        RouteOptimizationResult saved = routeOptimizationResultRepository.save(result);

        if (harvestCase != null) {
            harvestCase.addTimelineEntry(username,
                    "Route optimized: %.1f km (saved %.1f km from %.1f km original)%s"
                            .formatted(optimized.totalDistanceKm(), distanceSaved, originalDistanceKm,
                                    vehicle != null ? ", assigned to " + vehicle.getRegistrationNumber() : ""));
            harvestCaseRepository.save(harvestCase);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public RouteOptimizationResult getById(UUID id) {
        return routeOptimizationResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route result not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<RouteOptimizationResult> getByCase(UUID caseId) {
        return routeOptimizationResultRepository.findByHarvestCase_IdOrderByCreatedAtDesc(caseId);
    }

    /** Phase 5: market-order-scoped equivalent of {@link #getByCase}. */
    @Transactional(readOnly = true)
    public List<RouteOptimizationResult> getByMarketOrder(UUID marketOrderId) {
        return routeOptimizationResultRepository.findByMarketOrder_IdOrderByCreatedAtDesc(marketOrderId);
    }

    private RouteOptimizer.Point resolveStop(RouteStopRequest stop) {
        Double lat = stop.latitude();
        Double lng = stop.longitude();

        if (lat == null || lng == null) {
            List<MasterItem> candidates = masterItemRepository.findByNameIgnoreCase(stop.name());
            Optional<MasterItem> withCoordinates = candidates.stream()
                    .filter(m -> m.getLatitude() != null && m.getLongitude() != null)
                    .findFirst();
            if (withCoordinates.isEmpty()) {
                throw new InvalidLocationException(
                        "No coordinates given for stop '%s' and none are on file in master data. Provide latitude/longitude, or add coordinates to this location/company in Master Data."
                                .formatted(stop.name()));
            }
            lat = withCoordinates.get().getLatitude();
            lng = withCoordinates.get().getLongitude();
        }

        validateCoordinate(stop.name(), lat, lng);
        return new RouteOptimizer.Point(stop.name(), lat, lng);
    }

    private void validateCoordinate(String name, double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new InvalidLocationException(
                    "Coordinates for stop '%s' are out of range (lat=%.4f, lng=%.4f).".formatted(name, lat, lng));
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
