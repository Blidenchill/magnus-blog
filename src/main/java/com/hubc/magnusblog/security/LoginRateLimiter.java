package com.hubc.magnusblog.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for the login form: after 5 failures for the
 * same ip+username within 15 minutes, further attempts are refused for the rest
 * of the window. Single-instance only (the app runs as one instance by design).
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_SECONDS = 15 * 60;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public static String key(String ip, String username) {
        return (ip == null ? "-" : ip) + "|" + (username == null ? "-" : username.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isBlocked(String key) {
        Deque<Instant> deque = failures.get(key);
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            prune(deque);
            return deque.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(Instant.now());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private static void prune(Deque<Instant> deque) {
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }
}
