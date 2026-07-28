package com.adhar.kit.security.filter;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.ratelimit.InMemoryRateLimiterStore;
import com.adhar.kit.security.ratelimit.RateLimiterStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter for request throttling.
 *
 * <p>Implements a fixed-window rate limiting algorithm. The counting state is held
 * by a pluggable {@link RateLimiterStore}: the default {@link InMemoryRateLimiterStore}
 * keeps counters in a local map (single node), while a distributed implementation
 * (e.g. Redis-backed) enforces limits across a cluster. Limits are applied per
 * client IP address within a configurable time window.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>IP-based rate limiting</li>
 *   <li>Configurable request limit and time window</li>
 *   <li>Standard rate limit headers (X-RateLimit-*)</li>
 *   <li>Pluggable in-memory or distributed counter store</li>
 *   <li>Graceful 429 Too Many Requests response</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   security:
 *     rate-limit:
 *       enabled: true
 *       max-requests: 100
 *       window-seconds: 60
 *       store: memory   # or "redis"
 * }</pre>
 *
 * <p><b>Response Headers:</b></p>
 * <ul>
 *   <li>X-RateLimit-Limit - Maximum requests allowed</li>
 *   <li>X-RateLimit-Remaining - Requests remaining in current window</li>
 *   <li>X-RateLimit-Reset - Timestamp when the window resets</li>
 *   <li>Retry-After - Seconds until requests are allowed again (only on 429)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final AdharSecurityProperties.RateLimitProperties config;
    private final RateLimiterStore store;

    /**
     * Creates a rate limiting filter backed by the default in-memory store.
     *
     * @param config rate limit configuration
     */
    public RateLimitingFilter(AdharSecurityProperties.RateLimitProperties config) {
        this(config, new InMemoryRateLimiterStore());
    }

    /**
     * Creates a rate limiting filter backed by the supplied store.
     *
     * @param config rate limit configuration
     * @param store the counter store (in-memory or distributed)
     */
    public RateLimitingFilter(AdharSecurityProperties.RateLimitProperties config, RateLimiterStore store) {
        this.config = config;
        this.store = store;
        log.info("Rate limiting filter initialized: {} requests per {} seconds (store: {})",
            config.getMaxRequests(), config.getWindowSeconds(), store.getClass().getSimpleName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = getClientIdentifier(request);
        long windowMs = config.getWindowSeconds() * 1000L;

        RateLimiterStore.Decision decision = store.tryAcquire(clientId, config.getMaxRequests(), windowMs);

        addRateLimitHeaders(response, decision);

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {} ({} requests in {} seconds)",
                clientId, config.getMaxRequests(), config.getWindowSeconds());

            long retryAfterSeconds = Math.max(1, (decision.resetTimeMillis() - System.currentTimeMillis()) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in %d seconds.\",\"retryAfter\":%d}",
                retryAfterSeconds, retryAfterSeconds
            ));
        }
    }

    /**
     * Gets the client identifier for rate limiting.
     * Uses X-Forwarded-For header if present (for proxied requests), otherwise remote address.
     */
    private String getClientIdentifier(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Adds standard rate limit headers to the response.
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimiterStore.Decision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(config.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetTimeMillis() / 1000));
    }

    /**
     * Gets the current cache size (for monitoring). Only meaningful for the
     * in-memory store; distributed stores report {@code -1}.
     *
     * @return number of entries in an in-memory rate limit cache, or {@code -1}
     */
    public int getCacheSize() {
        if (store instanceof InMemoryRateLimiterStore inMemory) {
            return inMemory.getCacheSize();
        }
        return -1;
    }
}
