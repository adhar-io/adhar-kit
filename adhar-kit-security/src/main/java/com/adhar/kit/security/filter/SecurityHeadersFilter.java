package com.adhar.kit.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to add security headers to HTTP responses.
 * Adds headers like CSP, X-Frame-Options, X-XSS-Protection, etc.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "adhar.security.headers", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Content Security Policy
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // XSS Protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Prevent MIME sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy
        response.setHeader("Permissions-Policy", "geolocation=(self), microphone=()");

        // HSTS (HTTP Strict Transport Security)
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        log.debug("Security headers added to response for: {}", request.getRequestURI());

        filterChain.doFilter(request, response);
    }
}

