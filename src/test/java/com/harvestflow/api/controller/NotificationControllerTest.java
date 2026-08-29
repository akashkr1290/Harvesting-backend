package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code NotificationController} reads {@code @AuthenticationPrincipal
 * SecurityUser} on every endpoint, so — same reasoning documented in
 * {@code ProduceImageControllerAuthorizationTest} — every test here that
 * needs the controller body to actually run authenticates via a real
 * {@link SecurityUser} injected through {@code authentication(...)}, not
 * {@code @WithMockUser}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationService notificationService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userOnlySeesTheirOwnNotifications() throws Exception {
        notificationService.notify("alice", NotificationCategory.GENERAL, "For Alice", "msg", null, null);
        notificationService.notify("bob", NotificationCategory.GENERAL, "For Bob", "msg", null, null);

        mockMvc.perform(get("/api/notifications").with(authentication(authFor("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("For Alice"));
    }

    @Test
    void markingAnotherUsersNotificationAsReadIsNotFound() throws Exception {
        notificationService.notify("bob", NotificationCategory.GENERAL, "For Bob", "msg", null, null);
        String bobsList = mockMvc.perform(get("/api/notifications").with(authentication(authFor("bob"))))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(bobsList, "$[0].id");

        mockMvc.perform(post("/api/notifications/" + id + "/read").with(authentication(authFor("alice"))))
                .andExpect(status().isNotFound());
    }

    private UsernamePasswordAuthenticationToken authFor(String username) {
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
