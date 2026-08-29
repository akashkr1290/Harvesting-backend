package com.harvestflow.api.controller;

import com.harvestflow.api.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentController never reads {@code @AuthenticationPrincipal} directly
 * by type-casting in a way {@code @WithMockUser} would break for these
 * particular assertions (role checks only) — same reasoning as
 * AnalyticsControllerTest. The initiate endpoint DOES use
 * {@code @AuthenticationPrincipal SecurityUser}, but these tests only
 * exercise the reject-before-reaching-the-body paths (missing role /
 * invalid body), so that's never an issue here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void initiateWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRANSPORT_PERSON")
    void initiateWithWrongRoleIsForbidden() throws Exception {
        // A well-formed body is required here: @Valid argument resolution runs BEFORE
        // the @PreAuthorize AOP check fires on method invocation, so pairing wrong-role
        // with an invalid body would trip validation (400) before authorization ever
        // gets evaluated, masking the thing this test is actually checking.
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("{\"referenceType\":\"PURCHASE_INVOICE\",\"referenceId\":\"" + UUID.randomUUID() + "\",\"amount\":100.0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PURCHASE_ACCOUNT")
    void initiateWithMissingFieldsIsRejectedByValidation() throws Exception {
        // Correct role, but an empty body fails @NotNull/@Positive validation before
        // ever reaching the (mocked) service — confirms request validation is wired in.
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TRANSPORT_PERSON")
    void getByIdWithWrongRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
