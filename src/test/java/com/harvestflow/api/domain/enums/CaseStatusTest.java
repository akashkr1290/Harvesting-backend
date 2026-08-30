package com.harvestflow.api.domain.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state machine's correctness hinges entirely on nextActorRole() being
 * right — CaseService.assertTransition() trusts it completely to decide
 * who's allowed to act on a case. Any drift here is a real authorization
 * bug, not just a display issue.
 */
class CaseStatusTest {

    @Test
    void awaitingPlotSelection_isOwnedByPlotSelection() {
        assertThat(CaseStatus.AWAITING_PLOT_SELECTION.nextActorRole()).isEqualTo(UserRole.PLOT_SELECTION);
    }

    @Test
    void submittedForPlanning_isOwnedByPlanning() {
        assertThat(CaseStatus.SUBMITTED_FOR_PLANNING.nextActorRole()).isEqualTo(UserRole.PLANNING);
    }

    @Test
    void theTwoPurchaseAccountStages_bothRouteToPurchaseAccount() {
        // Purchase Account owns two different stages (rate update, then
        // invoice) — both must resolve to the same role so the frontend's
        // "which screen do I open" logic (keyed off this) works correctly.
        assertThat(CaseStatus.PLANNED.nextActorRole()).isEqualTo(UserRole.PURCHASE_ACCOUNT);
        assertThat(CaseStatus.MATERIAL_RECONCILED.nextActorRole()).isEqualTo(UserRole.PURCHASE_ACCOUNT);
    }

    @Test
    void theTwoGodownStages_bothRouteToGodown() {
        assertThat(CaseStatus.RATE_UPDATED.nextActorRole()).isEqualTo(UserRole.GODOWN);
        assertThat(CaseStatus.COST_ENTRIES_DONE.nextActorRole()).isEqualTo(UserRole.GODOWN);
    }

    @Test
    void onlySalesCompleted_isTerminal() {
        for (CaseStatus status : CaseStatus.values()) {
            if (status == CaseStatus.SALES_COMPLETED) {
                assertThat(status.isTerminal()).isTrue();
            } else {
                assertThat(status.isTerminal())
                        .as("%s should not be terminal", status)
                        .isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    void everyStatus_hasANonNullNextActorRole(CaseStatus status) {
        assertThat(status.nextActorRole()).isNotNull();
    }
}
