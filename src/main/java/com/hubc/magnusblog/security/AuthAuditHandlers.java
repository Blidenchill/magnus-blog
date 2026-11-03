package com.hubc.magnusblog.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.UUID;

/**
 * Login success / failure / logout handlers that write auth_event audit rows
 * and feed the rate limiter. Never logs credentials.
 */
@Component
public class AuthAuditHandlers implements AuthenticationSuccessHandler,
        AuthenticationFailureHandler,
        LogoutSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditHandlers.class);

    private final AuthEventRepository authEventRepository;
    private final LoginRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;

    public AuthAuditHandlers(AuthEventRepository authEventRepository,
                             LoginRateLimiter rateLimiter,
                             TransactionTemplate transactionTemplate) {
        this.authEventRepository = authEventRepository;
        this.rateLimiter = rateLimiter;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String username = authentication.getName();
        rateLimiter.recordSuccess(LoginRateLimiter.key(clientIp(request), username));
        record(username, clientIp(request), true, AuthEvent.TYPE_LOGIN, request.getHeader("User-Agent"));
        response.sendRedirect(request.getContextPath() + "/admin/dashboard");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        rateLimiter.recordFailure(LoginRateLimiter.key(clientIp(request), username));
        record(username, clientIp(request), false, AuthEvent.TYPE_LOGIN, request.getHeader("User-Agent"));
        response.sendRedirect(request.getContextPath() + "/admin/login?error");
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        if (authentication != null) {
            record(authentication.getName(), clientIp(request), true, AuthEvent.TYPE_LOGOUT,
                    request.getHeader("User-Agent"));
        }
        response.sendRedirect(request.getContextPath() + "/admin/login?logout");
    }

    public void recordLocked(String username, HttpServletRequest request) {
        record(username, clientIp(request), false, AuthEvent.TYPE_LOCKED, request.getHeader("User-Agent"));
    }

    private void record(String username, String ip, boolean success, String type, String userAgent) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                AuthEvent event = new AuthEvent();
                event.setId(UUID.randomUUID());
                event.setUsername(truncate(username, 120));
                event.setIp(truncate(ip, 64));
                event.setSuccess(success);
                event.setType(type);
                event.setUserAgent(truncate(userAgent, 300));
                authEventRepository.save(event);
            });
        } catch (Exception e) {
            // Auditing must never break authentication flows.
            log.warn("failed to record auth event: {}", e.getMessage());
        }
    }

    static String clientIp(HttpServletRequest request) {
        // Only the container-facing remote address is trusted; X-Forwarded-For is
        // handled by Spring's ForwardedHeaderFilter only when the proxy is trusted.
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
