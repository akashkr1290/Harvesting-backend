package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.dto.auth.ChangePasswordRequest;
import com.harvestflow.api.dto.auth.LoginRequest;
import com.harvestflow.api.dto.auth.LoginResponse;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                // Same message whether the user doesn't exist or the password
                // is wrong — don't leak which, that's a username-enumeration
                // vector.
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

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
    }
}
