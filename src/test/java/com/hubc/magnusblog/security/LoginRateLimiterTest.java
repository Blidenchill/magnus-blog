package com.hubc.magnusblog.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void blocksAfterFiveFailures() {
        String key = LoginRateLimiter.key("1.2.3.4", "admin");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure(key);
            assertThat(limiter.isBlocked(key)).as("failure %d", i + 1).isFalse();
        }
        limiter.recordFailure(key);
        assertThat(limiter.isBlocked(key)).isTrue();
    }

    @Test
    void successClearsFailures() {
        String key = LoginRateLimiter.key("1.2.3.4", "admin");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(key);
        }
        limiter.recordSuccess(key);
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void keysAreScopedPerIpAndUser() {
        String a = LoginRateLimiter.key("1.1.1.1", "admin");
        String b = LoginRateLimiter.key("2.2.2.2", "admin");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(a);
        }
        assertThat(limiter.isBlocked(b)).isFalse();
    }
}
