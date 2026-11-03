package com.hubc.magnusblog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Refuses login attempts while the ip+username pair is inside the lockout window. */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter rateLimiter;
    private final AuthAuditHandlers auditHandlers;

    public LoginRateLimitFilter(LoginRateLimiter rateLimiter, AuthAuditHandlers auditHandlers) {
        this.rateLimiter = rateLimiter;
        this.auditHandlers = auditHandlers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + "/admin/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String key = LoginRateLimiter.key(AuthAuditHandlers.clientIp(request), request.getParameter("username"));
        if (rateLimiter.isBlocked(key)) {
            auditHandlers.recordLocked(request.getParameter("username"), request);
            response.sendRedirect(request.getContextPath() + "/admin/login?error");
            return;
        }
        chain.doFilter(request, response);
    }
}
