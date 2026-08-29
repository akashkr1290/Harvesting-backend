package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.LoginHistory;
import com.harvestflow.api.domain.entity.PasswordResetToken;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.dto.auth.ChangePasswordRequest;
import com.harvestflow.api.dto.auth.LoginRequest;
import com.harvestflow.api.dto.auth.LoginResponse;
import com.harvestflow.api.repository.LoginHistoryRepository;
import com.harvestflow.api.repository.PasswordResetTokenRepository;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.security.JwtService;
import com.harvestflow.api.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetNotificationService passwordResetNotificationService;

    @Value("${harvestflow.password-reset.token-expiry-minutes}")
    private long resetTokenExpiryMinutes;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        loginRateLimiter.assertNotLocked(request.username());

        Optional<User> maybeUser = userRepository.findByUsername(request.username());

        if (maybeUser.isEmpty()) {
            loginRateLimiter.recordFailure(request.username());
            log.warn("Login attempt for unknown username '{}' from {}", request.username(), ipAddress);
            throw new BadCredentialsException("Invalid username or password");
        }

        User user = maybeUser.get();

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(request.username());
            recordHistory(user, ipAddress, false, !user.isActive() ? "Account inactive" : "Incorrect password");
            log.warn("Failed login for user '{}' from {}", user.getUsername(), ipAddress);
            throw new BadCredentialsException("Invalid username or password");
        }

        loginRateLimiter.recordSuccess(request.username());
        user.setLastActiveAt(Instant.now());
        userRepository.save(user);
        recordHistory(user, ipAddress, true, null);
        log.info("Successful login for user '{}' from {}", user.getUsername(), ipAddress);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        return new LoginResponse(
                token,
                user.getId().toString(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                user.isMustChangePassword()
        );
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("User '{}' changed their own password", user.getUsername());
    }

    @Transactional
    public void requestPasswordReset(String username) {
        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for unknown username '{}' — no-op.", username);
            return;
        }

        User user = maybeUser.get();
        String token = generateSecureToken();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenExpiryMinutes)));
        passwordResetTokenRepository.save(resetToken);

        passwordResetNotificationService.notifyPasswordReset(user.getEmail(), user.getUsername(), token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new BadCredentialsException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset completed via token for user '{}'", user.getUsername());
    }

    private void recordHistory(User user, String ipAddress, boolean success, String failureReason) {
        LoginHistory entry = new LoginHistory();
        entry.setUser(user);
        entry.setUsername(user.getUsername());
        entry.setIpAddress(ipAddress);
        entry.setSuccess(success);
        entry.setFailureReason(failureReason);
        loginHistoryRepository.save(entry);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
