package com.harvestflow.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AnalyticsController never reads {@code @AuthenticationPrincipal}, so
 * {@code @WithMockUser} (Spring's built-in User principal) is fine here —
 * unlike ProduceImageControllerAuthorizationTest's upload endpoint, nothing
 * in this controller ever tries to cast the principal to this app's
 * {@code SecurityUser}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void verificationStatsRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/analytics/verification"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void verificationStatsIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/analytics/verification"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void verificationStatsReturnsShapeWithNoDataUploaded() throws Exception {
        mockMvc.perform(get("/api/analytics/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImages").isNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sihImpactAlwaysReturnsAllSevenLabeledMetrics() throws Exception {
        mockMvc.perform(get("/api/analytics/sih-impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.length()").value(7))
                .andExpect(jsonPath("$.metrics[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.metrics[1].status").value("DEMO_DATA"))
                .andExpect(jsonPath("$.metrics[2].status").value("NOT_AVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void operationalStatsIsAccessibleToAdmin() throws Exception {
        mockMvc.perform(get("/api/analytics/operational"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").isNumber());
    }
}
