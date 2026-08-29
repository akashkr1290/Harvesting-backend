package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.ProduceImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the authorization boundary on {@link ProduceImageController}: the
 * upload endpoint is role-gated the same way {@code UploadController}'s
 * endpoints are, and an unauthenticated request is rejected before it ever
 * reaches {@link ProduceImageService}.
 *
 * <p><b>Note on {@code @WithMockUser}:</b> it populates the security context
 * with Spring's own {@code org.springframework.security.core.userdetails.User}
 * as the principal — a different class from this app's {@link SecurityUser}.
 * That's fine for the 403/401 tests below, since {@code @PreAuthorize}
 * rejects the request from its granted authorities alone, before the
 * controller method (and its {@code @AuthenticationPrincipal SecurityUser}
 * parameter) is ever reached. It is NOT fine for a test that expects the
 * controller body to actually run: {@code @AuthenticationPrincipal} would
 * fail to cast Spring's {@code User} to {@code SecurityUser} and silently
 * resolve to {@code null}, so the success-path test below builds a real
 * {@link SecurityUser} and injects it via {@code authentication(...)}
 * instead of {@code @WithMockUser}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProduceImageControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProduceImageService produceImageService;

    @Test
    void uploadWithoutAuthenticationIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "crop.png", "image/png", new byte[]{1, 2, 3});

        // This app configures no AuthenticationEntryPoint (no .formLogin()/.httpBasic() in
        // SecurityConfig), so Spring Security's default fallback (Http403ForbiddenEntryPoint)
        // returns 403 here, not 401 — same status as the wrong-role case below. That's an
        // existing, app-wide characteristic of SecurityConfig, not something Phase 4 changes.
        mockMvc.perform(multipart("/api/produce-images").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRANSPORT_PERSON")
    void uploadWithWrongRoleIsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "crop.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/produce-images").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadWithCorrectRoleIsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "crop.png", "image/png", new byte[]{1, 2, 3});
        when(produceImageService.upload(any(), any(), any(), anyBoolean(), eq("plot.tester")))
                .thenReturn(null);

        mockMvc.perform(multipart("/api/produce-images").file(file)
                        .with(authentication(plotSelectionAuth("plot.tester"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TRANSPORT_PERSON")
    void deleteWithWrongRoleIsForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/produce-images/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR") // a real Phase 4 upload role, but not ADMIN — the review queue is stricter
    void reviewQueueRequiresAdminNotJustAnyUploadRole() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/produce-images/review-queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reviewQueueIsAccessibleToAdmin() throws Exception {
        when(produceImageService.getReviewQueue()).thenReturn(java.util.List.of());
        // ADMIN role check passes via @PreAuthorize alone; the method takes no
        // @AuthenticationPrincipal, so @WithMockUser's principal type mismatch is irrelevant here.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/produce-images/review-queue"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void adminDecisionRequiresAdminNotJustAnyUploadRole() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/produce-images/" + UUID.randomUUID() + "/admin-decision")
                        .contentType("application/json")
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDecisionSucceedsForAdmin() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(produceImageService.adminDecide(eq(imageId), any(), eq("admin.tester"), any()))
                .thenReturn(null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/produce-images/" + imageId + "/admin-decision")
                        .with(authentication(adminAuth("admin.tester")))
                        .contentType("application/json")
                        .content("{\"decision\":\"REJECT\",\"note\":\"Clearly AI-generated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminDecisionWithMissingDecisionFieldIsRejectedByValidation() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/produce-images/" + UUID.randomUUID() + "/admin-decision")
                        .with(authentication(adminAuth("admin.tester")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken plotSelectionAuth(String username) {
        User user = new User();
        user.setUsername(username);
        user.setName(username);
        user.setEmail(username + "@example.test");
        user.setRole(UserRole.PLOT_SELECTION);
        user.setPasswordHash("irrelevant-not-checked-in-this-test");
        SecurityUser securityUser = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken adminAuth(String username) {
        User user = new User();
        user.setUsername(username);
        user.setName(username);
        user.setEmail(username + "@example.test");
        user.setRole(UserRole.ADMIN);
        user.setPasswordHash("irrelevant-not-checked-in-this-test");
        SecurityUser securityUser = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
    }
}
