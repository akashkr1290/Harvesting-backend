package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.cases.PlanningRequest;
import com.harvestflow.api.dto.cases.PlotSelectionRequest;
import com.harvestflow.api.dto.cases.PurchaseRateRequest;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidTransitionException;
import com.harvestflow.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This is the single most important test in the project: it verifies that
 * CaseService.assertTransition actually enforces both halves of the rule
 * it claims to (correct current status AND correct role) against a real
 * database, not just by reading the source and reasoning about it.
 *
 * @Transactional rolls back after each test, so tests don't interfere
 * with each other despite sharing the same Spring context / H2 instance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CaseServiceStateMachineTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User plotSelectionUser;
    private User planningUser;
    private User purchaseAccountUser;

    @BeforeEach
    void setUp() {
        plotSelectionUser = createUser("plot.tester", UserRole.PLOT_SELECTION);
        planningUser = createUser("plan.tester", UserRole.PLANNING);
        purchaseAccountUser = createUser("purchase.tester", UserRole.PURCHASE_ACCOUNT);
    }

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setName(username);
        user.setMobile("9000000000");
        user.setEmail(username + "@test.local");
        user.setRole(role);
        user.setLocation("Test Location");
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("irrelevant-for-this-test"));
        user.setActive(true);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    private PlotSelectionRequest samplePlotSelectionRequest() {
        return new PlotSelectionRequest(
                "Test Location", LocalDate.now(), "Test Agent", "Test Farmer", "9876543210",
                "Test Village", 100, LocalDate.now().plusDays(1), "", 500.0, 10.0, 40.0,
                "Test Company", "PLT-001", "", "medium", null
        );
    }

    @Test
    void creatingAPlotSelection_startsInSubmittedForPlanningStatus() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);

        assertThat(created.getStatus()).isEqualTo(CaseStatus.SUBMITTED_FOR_PLANNING);
        assertThat(created.getFarmerName()).isEqualTo("Test Farmer");
        assertThat(created.getTimeline()).hasSize(1);
    }

    @Test
    void savingAPlan_withTheWrongRole_isRejectedWithForbiddenRoleException() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);
        PlanningRequest planningRequest = new PlanningRequest(
                LocalDate.now().plusDays(1), "Morning", "Supervisor Name", "Pickup Person",
                "", 500.0, "", ""
        );

        // Plot Selection tries to act on a stage that belongs to Planning.
        assertThatThrownBy(() -> caseService.savePlanning(created.getId(), planningRequest, plotSelectionUser))
                .isInstanceOf(ForbiddenRoleException.class);

        // And the case must be completely unaffected by the rejected attempt.
        HarvestCase reloaded = caseService.getById(created.getId());
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.SUBMITTED_FOR_PLANNING);
        assertThat(reloaded.getPlanning()).isNull();
    }

    @Test
    void savingAPlan_withTheCorrectRole_advancesStatusToPlanned() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);
        PlanningRequest planningRequest = new PlanningRequest(
                LocalDate.now().plusDays(1), "Morning", "Supervisor Name", "Pickup Person",
                "", 500.0, "", ""
        );

        HarvestCase planned = caseService.savePlanning(created.getId(), planningRequest, planningUser);

        assertThat(planned.getStatus()).isEqualTo(CaseStatus.PLANNED);
        assertThat(planned.getPlanning()).isNotNull();
        assertThat(planned.getPlanning().getSupervisorName()).isEqualTo("Supervisor Name");
    }

    @Test
    void repeatingAnAlreadyCompletedStage_isRejectedWithInvalidTransitionException() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);
        PlanningRequest planningRequest = new PlanningRequest(
                LocalDate.now().plusDays(1), "Morning", "Supervisor Name", "Pickup Person",
                "", 500.0, "", ""
        );
        caseService.savePlanning(created.getId(), planningRequest, planningUser);

        // Case is now PLANNED — trying to plan it again must fail, even
        // with the right role, because the status no longer matches.
        assertThatThrownBy(() -> caseService.savePlanning(created.getId(), planningRequest, planningUser))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void skippingAheadToPurchaseRate_beforePlanningHappens_isRejected() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);
        PurchaseRateRequest rateRequest = new PurchaseRateRequest(25.0);

        // Even with the correct role for the *eventual* rate-update stage,
        // this must fail because Planning hasn't happened yet — the case
        // is still SUBMITTED_FOR_PLANNING, not PLANNED.
        assertThatThrownBy(() -> caseService.updatePurchaseRate(created.getId(), rateRequest, purchaseAccountUser))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void theFullHappyPath_throughRateUpdate_leavesAConsistentTimeline() {
        HarvestCase created = caseService.createPlotSelection(samplePlotSelectionRequest(), plotSelectionUser);
        PlanningRequest planningRequest = new PlanningRequest(
                LocalDate.now().plusDays(1), "Morning", "Supervisor Name", "Pickup Person",
                "", 500.0, "", ""
        );
        caseService.savePlanning(created.getId(), planningRequest, planningUser);
        HarvestCase rated = caseService.updatePurchaseRate(created.getId(), new PurchaseRateRequest(25.0), purchaseAccountUser);

        assertThat(rated.getStatus()).isEqualTo(CaseStatus.RATE_UPDATED);
        assertThat(rated.getRatePerUnit()).isEqualTo(25.0);
        // One timeline entry per completed stage: create, plan, rate.
        assertThat(rated.getTimeline()).hasSize(3);
    }
}
