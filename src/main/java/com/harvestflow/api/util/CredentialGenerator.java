package com.harvestflow.api.util;

import com.harvestflow.api.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CredentialGenerator {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    public CredentialGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** e.g. "Priya Sharma" -> "priya.sharma", with a numeric suffix on collision. */
    public String generateUsername(String fullName) {
        String base = fullName.trim().toLowerCase().replaceAll("\\s+", ".").replaceAll("[^a-z0-9.]", "");
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    /** Temporary password the user must change at next login. */
    public String generateTempPassword() {
        StringBuilder sb = new StringBuilder("Hv");
        for (int i = 0; i < 8; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
