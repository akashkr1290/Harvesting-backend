package com.harvestflow.api.security;

import com.harvestflow.api.exception.AccountLockedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final class Attempt {
        int failureCount = 0;
        Instant lockedUntil = Instant.EPOCH;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockoutDuration;

    public LoginRateLimiter(
            @Value("${harvestflow.login.max-attempts}") int maxAttempts,
            @Value("${harvestflow.login.lockout-minutes}") long lockoutMinutes
    ) {
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutMinutes);
    }

    public synchronized void assertNotLocked(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null) return;

        if (attempt.failureCount >= maxAttempts && Instant.now().isBefore(attempt.lockedUntil)) {
            long minutesLeft = Math.max(1, Duration.between(Instant.now(), attempt.lockedUntil).toMinutes() + 1);
            throw new AccountLockedException(
                    "Too many failed login attempts. Try again in " + minutesLeft + " minute(s).");
        }
    }

    public synchronized void recordFailure(String username) {
        Attempt attempt = attempts.computeIfAbsent(key(username), k -> new Attempt());
        attempt.failureCount++;
        if (attempt.failureCount >= maxAttempts) {
            attempt.lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    public synchronized void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username.trim().toLowerCase();
    }

    public synchronized void reset() {
        attempts.clear();
    }
}
