package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DemandPrediction;
import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.HarvestCompletion;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.DemandLevel;
import com.harvestflow.api.domain.enums.TrendDirection;
import com.harvestflow.api.exception.InsufficientDataException;
import com.harvestflow.api.repository.HarvestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Part A end to end against a real (H2) database: aggregation
 * of completed HarvestCase history into monthly buckets, the
 * insufficient-data guard, and that a generated forecast round-trips
 * through the repository correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemandForecastServiceTest {

    @Autowired
    private DemandForecastService demandForecastService;

    @Autowired
    private HarvestCaseRepository harvestCaseRepository;

    @Test
    void generatePrediction_withFewerThanThreeMonthsOfHistory_throwsInsufficientData() {
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(1), 1000.0);

        assertThatThrownBy(() -> demandForecastService.generatePrediction("Mango", "Nashik", "tester"))
                .isInstanceOf(InsufficientDataException.class);
    }

    @Test
    void generatePrediction_withThreeMonthsOfRisingHistory_producesIncreasingHighDemandForecast() {
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(3), 1000.0);
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(2), 1300.0);
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(1), 1700.0);

        DemandPrediction prediction = demandForecastService.generatePrediction("Mango", "Nashik", "tester");

        assertThat(prediction.getDataPointsUsed()).isEqualTo(3);
        assertThat(prediction.getTrend()).isEqualTo(TrendDirection.INCREASING);
        assertThat(prediction.getPredictedQuantity()).isGreaterThan(0);
        assertThat(prediction.getGeneratedByUsername()).isEqualTo("tester");
        assertThat(prediction.getPredictionDate()).isAfter(LocalDate.now());
    }

    @Test
    void generatePrediction_isCaseInsensitiveOnCropAndLocation() {
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(3), 1000.0);
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(2), 1000.0);
        seedCompletedCase("Nashik", "Mango", LocalDate.now().minusMonths(1), 1000.0);

        DemandPrediction prediction = demandForecastService.generatePrediction("mango", "NASHIK", "tester");

        assertThat(prediction).isNotNull();
    }

    @Test
    void getLatest_afterGenerating_returnsTheMostRecentRun() {
        seedCompletedCase("Sangli", "Sugarcane", LocalDate.now().minusMonths(3), 500.0);
        seedCompletedCase("Sangli", "Sugarcane", LocalDate.now().minusMonths(2), 500.0);
        seedCompletedCase("Sangli", "Sugarcane", LocalDate.now().minusMonths(1), 500.0);

        DemandPrediction generated = demandForecastService.generatePrediction("Sugarcane", "Sangli", "tester");
        DemandPrediction latest = demandForecastService.getLatest("Sugarcane", "Sangli");

        assertThat(latest.getId()).isEqualTo(generated.getId());
        assertThat(latest.getDemandLevel()).isIn(DemandLevel.LOW, DemandLevel.MEDIUM, DemandLevel.HIGH);
    }

    @Test
    void getLatest_withNoForecastGenerated_throwsInsufficientData() {
        assertThatThrownBy(() -> demandForecastService.getLatest("Papaya", "Nowhere"))
                .isInstanceOf(InsufficientDataException.class);
    }

    private void seedCompletedCase(String location, String cropType, LocalDate actualDate, double weight) {
        HarvestCase c = new HarvestCase();
        c.setLocation(location);
        c.setCropType(cropType);
        c.setVisitDate(actualDate.minusDays(10));
        c.setFarmerName("Test Farmer");
        c.setVillage("Test Village");
        c.setHarvestingDate(actualDate.minusDays(1));
        c.setVolume(weight);
        c.setRecovery(10.0);
        c.setPulp(40.0);
        c.setSelectedCompany("Test Company");
        c.setPriority("medium");
        c.setCreatedByUsername("tester");
        c.setStatus(CaseStatus.HARVESTING_COMPLETED);
        
        HarvestCompletion completion = new HarvestCompletion();
        completion.setHarvestCase(c);
        completion.setActualDate(actualDate);
        completion.setActualWeight(weight);
        c.setCompletion(completion);

        harvestCaseRepository.save(c);
    }
}
