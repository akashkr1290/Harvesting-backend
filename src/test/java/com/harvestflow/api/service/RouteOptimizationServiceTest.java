package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DeliveryVehicle;
import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.RouteStatus;
import com.harvestflow.api.dto.logistics.RouteOptimizeRequest;
import com.harvestflow.api.dto.logistics.RouteStopRequest;
import com.harvestflow.api.exception.InvalidLocationException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.exception.VehicleCapacityExceededException;
import com.harvestflow.api.repository.DeliveryVehicleRepository;
import com.harvestflow.api.repository.HarvestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Part B (route optimization against real coordinates and a real
 * vehicle capacity check) and Part C (the case-linking integration point)
 * end to end against a real (H2) database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RouteOptimizationServiceTest {

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private DeliveryVehicleRepository deliveryVehicleRepository;

    @Autowired
    private HarvestCaseRepository harvestCaseRepository;

    @Test
    void optimize_withExplicitCoordinates_computesADistanceSavingRoute() {
        RouteOptimizeRequest request = new RouteOptimizeRequest(
                null, null, null,
                new RouteStopRequest("Farm A", 19.9975, 73.7898),
                List.of(
                        new RouteStopRequest("Farthest Buyer", 20.9, 73.7898),
                        new RouteStopRequest("Nearest Buyer", 20.05, 73.7898)
                ),
                500.0, null
        );

        var result = routeOptimizationService.optimize(request, "tester");

        assertThat(result.getStops()).hasSize(3);
        assertThat(result.getStops().get(0).getName()).isEqualTo("Farm A");
        assertThat(result.getOptimizedDistanceKm()).isLessThanOrEqualTo(result.getOriginalDistanceKm());
        assertThat(result.getEstimatedDurationMinutes()).isGreaterThan(0);
        assertThat(result.getEstimatedCost()).isGreaterThan(0);
        assertThat(result.getStatus()).isEqualTo(RouteStatus.OPTIMIZED);
    }

    @Test
    void optimize_withoutCoordinatesAndNoMasterDataMatch_throwsInvalidLocation() {
        RouteOptimizeRequest request = new RouteOptimizeRequest(
                null, null, null,
                new RouteStopRequest("Some Unknown Farm", null, null),
                List.of(new RouteStopRequest("Some Unknown Buyer", null, null)),
                100.0, null
        );

        assertThatThrownBy(() -> routeOptimizationService.optimize(request, "tester"))
                .isInstanceOf(InvalidLocationException.class);
    }

    @Test
    void optimize_loadExceedingVehicleCapacity_throwsVehicleCapacityExceeded() {
        DeliveryVehicle vehicle = new DeliveryVehicle();
        vehicle.setRegistrationNumber("TEST-CAP-1");
        vehicle.setVehicleType("Tractor Trolley");
        vehicle.setCapacityKg(200.0);
        vehicle = deliveryVehicleRepository.save(vehicle);

        RouteOptimizeRequest request = new RouteOptimizeRequest(
                null, null, vehicle.getId(),
                new RouteStopRequest("Farm A", 19.9975, 73.7898),
                List.of(new RouteStopRequest("Buyer B", 20.05, 73.7898)),
                5000.0, null
        );

        assertThatThrownBy(() -> routeOptimizationService.optimize(request, "tester"))
                .isInstanceOf(VehicleCapacityExceededException.class);
    }

    @Test
    void optimize_withUnknownVehicleId_throwsResourceNotFound() {
        RouteOptimizeRequest request = new RouteOptimizeRequest(
                null, null, UUID.randomUUID(),
                new RouteStopRequest("Farm A", 19.9975, 73.7898),
                List.of(new RouteStopRequest("Buyer B", 20.05, 73.7898)),
                100.0, null
        );

        assertThatThrownBy(() -> routeOptimizationService.optimize(request, "tester"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void optimize_withValidVehicleWithinCapacity_assignsVehicleAndSetsStatusAssigned() {
        DeliveryVehicle vehicle = new DeliveryVehicle();
        vehicle.setRegistrationNumber("TEST-CAP-2");
        vehicle.setVehicleType("Eicher Truck");
        vehicle.setCapacityKg(5000.0);
        vehicle = deliveryVehicleRepository.save(vehicle);

        RouteOptimizeRequest request = new RouteOptimizeRequest(
                null, null, vehicle.getId(),
                new RouteStopRequest("Farm A", 19.9975, 73.7898),
                List.of(new RouteStopRequest("Buyer B", 20.05, 73.7898)),
                1000.0, 25.0
        );

        var result = routeOptimizationService.optimize(request, "tester");

        assertThat(result.getVehicle().getId()).isEqualTo(vehicle.getId());
        assertThat(result.getStatus()).isEqualTo(RouteStatus.ASSIGNED);
    }

    @Test
    void optimize_withCaseId_linksResultAndAddsTimelineEntryToTheCase() {
        HarvestCase harvestCase = new HarvestCase();
        harvestCase.setLocation("Nashik");
        harvestCase.setCropType("Mango");
        harvestCase.setVisitDate(LocalDate.now());
        harvestCase.setFarmerName("Test Farmer");
        harvestCase.setVillage("Test Village");
        harvestCase.setHarvestingDate(LocalDate.now().plusDays(1));
        harvestCase.setVolume(500.0);
        harvestCase.setRecovery(10.0);
        harvestCase.setPulp(40.0);
        harvestCase.setSelectedCompany("Test Company");
        harvestCase.setPriority("medium");
        harvestCase.setCreatedByUsername("tester");
        harvestCase = harvestCaseRepository.save(harvestCase);
        int timelineSizeBefore = harvestCase.getTimeline().size();

        RouteOptimizeRequest request = new RouteOptimizeRequest(
                harvestCase.getId(), null, null,
                new RouteStopRequest("Farm A", 19.9975, 73.7898),
                List.of(new RouteStopRequest("Buyer B", 20.05, 73.7898)),
                300.0, null
        );

        var result = routeOptimizationService.optimize(request, "tester");

        assertThat(result.getHarvestCase().getId()).isEqualTo(harvestCase.getId());

        HarvestCase reloaded = harvestCaseRepository.findById(harvestCase.getId()).orElseThrow();
        assertThat(reloaded.getTimeline()).hasSize(timelineSizeBefore + 1);

        List<com.harvestflow.api.domain.entity.RouteOptimizationResult> byCase =
                routeOptimizationService.getByCase(harvestCase.getId());
        assertThat(byCase).extracting(r -> r.getId()).contains(result.getId());
    }
}
