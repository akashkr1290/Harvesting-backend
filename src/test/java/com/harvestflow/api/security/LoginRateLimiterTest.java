package com.harvestflow.api.security;

import com.harvestflow.api.exception.AccountLockedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void allowsLoginUntilMaxAttemptsIsReached() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);

        limiter.assertNotLocked("alice"); // 0 failures — fine
        limiter.recordFailure("alice");
        limiter.assertNotLocked("alice"); // 1 failure — still fine
        limiter.recordFailure("alice");
        limiter.assertNotLocked("alice"); // 2 failures — still fine
        limiter.recordFailure("alice");   // 3rd failure — now locked

        assertThatThrownBy(() -> limiter.assertNotLocked("alice"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCount() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);

        limiter.recordFailure("bob");
        limiter.recordFailure("bob");
        limiter.recordSuccess("bob");

        // Back to zero — two more failures shouldn't lock the account.
        limiter.recordFailure("bob");
        limiter.recordFailure("bob");
        limiter.assertNotLocked("bob");
    }

    @Test
    void lockoutIsPerUsername_notGlobal() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 15);

        limiter.recordFailure("carol");
        limiter.recordFailure("carol"); // carol is now locked

        // dave is unaffected by carol's lockout.
        limiter.assertNotLocked("dave");
    }

    @Test
    void usernameMatchingIsCaseInsensitiveAndTrimmed() {
        // Otherwise "Dave" and "dave " and "dave" would each get their own
        // separate attempt counter, defeating the whole point of rate
        // limiting by username.
        LoginRateLimiter limiter = new LoginRateLimiter(2, 15);

        limiter.recordFailure("Dave");
        limiter.recordFailure(" dave ");

        assertThatThrownBy(() -> limiter.assertNotLocked("DAVE"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void unknownUsernameWithNoRecordedAttempts_isNeverLocked() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);
        limiter.assertNotLocked("never-seen-before");
    }
    
}
