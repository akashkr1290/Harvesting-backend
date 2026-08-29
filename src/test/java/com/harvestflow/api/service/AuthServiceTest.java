package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.auth.LoginRequest;
import com.harvestflow.api.dto.auth.LoginResponse;
import com.harvestflow.api.exception.AccountLockedException;
import com.harvestflow.api.repository.LoginHistoryRepository;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.security.LoginRateLimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    private User user;

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        loginRateLimiter.recordSuccess("auth.tester");
        user = new User();
        user.setName("Test User");
        user.setMobile("9000000000");
        user.setEmail("auth.test@test.local");
        user.setRole(UserRole.ADMIN);
        user.setLocation("Test Location");
        user.setUsername("auth.tester");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setActive(true);
        user.setMustChangePassword(false);
        user = userRepository.save(user);
    }

    @Test
    void correctCredentials_returnATokenAndRecordSuccessfulHistory() {
        LoginResponse response = authService.login(
                new LoginRequest("auth.tester", "correct-password"), "127.0.0.1");

        assertThat(response.token()).isNotBlank();
        assertThat(response.username()).isEqualTo("auth.tester");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);

        assertThat(loginHistoryRepository.findTop50ByUserOrderByLoggedInAtDesc(user))
                .hasSize(1)
                .allSatisfy(entry -> assertThat(entry.isSuccess()).isTrue());
    }

    @Test
    void wrongPassword_isRejectedAndRecordedAsAFailure() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("auth.tester", "wrong-password"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(loginHistoryRepository.findTop50ByUserOrderByLoggedInAtDesc(user))
                .hasSize(1)
                .allSatisfy(entry -> assertThat(entry.isSuccess()).isFalse());
    }

    @Test
    void doesNotRevealWhetherAUsernameExists_sameExceptionEitherWay() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("no-such-user", "whatever"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("auth.tester", "wrong-password"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void repeatedFailures_eventuallyLockTheAccount() {
        // application-test.yml doesn't override harvestflow.login.max-attempts,
        // so this uses the same default as dev/prod (5).
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("auth.tester", "wrong-password"), "127.0.0.1"));
        }

        // The 6th attempt — even with the CORRECT password — must be
        // rejected by the lockout, not allowed through.
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("auth.tester", "correct-password"), "127.0.0.1"))
                .isInstanceOf(AccountLockedException.class);
    }
}
