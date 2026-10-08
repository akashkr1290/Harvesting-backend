package com.harvestflow.api.security;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guards the fixes for the blocker findings in the October 2026 review. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    /** The registration module used to be outside the component scan, so this returned 404. */
    @Test
    void registrationEndpointExists() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketplaceBuyerCannotListInternalCases() throws Exception {
        mockMvc.perform(get("/api/cases").with(authentication(authAs(UserRole.CONSUMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void marketplaceFarmerCannotExportCases() throws Exception {
        mockMvc.perform(get("/api/cases/export").with(authentication(authAs(UserRole.FARMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void marketplaceBuyerCannotReadReports() throws Exception {
        mockMvc.perform(get("/api/reports/summary").with(authentication(authAs(UserRole.BULK_BUYER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalStaffCanStillListCases() throws Exception {
        mockMvc.perform(get("/api/cases").with(authentication(authAs(UserRole.PLANNING))))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken authAs(UserRole role) {
        User user = new User();
        user.setUsername("test-" + role.name().toLowerCase());
        user.setName("Test " + role.name());
        user.setEmail(user.getUsername() + "@example.test");
        user.setRole(role);
        user.setPasswordHash("irrelevant");
        SecurityUser securityUser = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
    }
}
