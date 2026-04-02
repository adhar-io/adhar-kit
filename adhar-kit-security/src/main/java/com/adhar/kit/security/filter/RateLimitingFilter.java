package com.adhar.kit.security.filter;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting filter for request throttling.
 *
 * <p>Implements a sliding window rate limiting algorithm using an in-memory cache.
 * Limits requests per client IP address within a configurable time window.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>IP-based rate limiting</li>
 *   <li>Configurable request limit and time window</li>
 *   <li>Standard rate limit headers (X-RateLimit-*)</li>
 *   <li>Automatic cache cleanup</li>
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
    private final Map<String, RateLimitEntry> rateLimitCache = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute

    /**
     * Rate limit entry for tracking requests per client.
     */
    private static class RateLimitEntry {
        final AtomicInteger requestCount = new AtomicInteger(0);
        volatile long windowStart;

        RateLimitEntry() {
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();

            // Check if window has expired
            if (now - windowStart >= windowMs) {
                // Reset window
                windowStart = now;
                requestCount.set(1);
                return true;
            }

            // Check if under limit
            if (requestCount.get() < maxRequests) {
                requestCount.incrementAndGet();
                return true;
            }

            return false;
        }

        int getRemainingRequests(int maxRequests) {
            return Math.max(0, maxRequests - requestCount.get());
        }

        long getResetTime(long windowMs) {
            return windowStart + windowMs;
        }
    }

    /**
     * Creates rate limiting filter.
     *
     * @param config rate limit configuration
     */
    public RateLimitingFilter(AdharSecurityProperties.RateLimitProperties config) {
        this.config = config;
        log.info("Rate limiting filter initialized: {} requests per {} seconds",
            config.getMaxRequests(), config.getWindowSeconds());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Clean up old entries periodically
        cleanupOldEntries();

        // Get client identifier (IP address)
        String clientId = getClientIdentifier(request);

        // Get or create rate limit entry
        RateLimitEntry entry = rateLimitCache.computeIfAbsent(clientId, k -> new RateLimitEntry());

        long windowMs = config.getWindowSeconds() * 1000L;

        // Try to acquire a request slot
        if (entry.tryAcquire(config.getMaxRequests(), windowMs)) {
            // Add rate limit headers
            addRateLimitHeaders(response, entry, windowMs);

            // Proceed with request
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for client: {} ({} requests in {} seconds)",
                clientId, config.getMaxRequests(), config.getWindowSeconds());

            // Add rate limit headers
            addRateLimitHeaders(response, entry, windowMs);

            // Calculate retry-after
            long resetTime = entry.getResetTime(windowMs);
            long retryAfterSeconds = Math.max(1, (resetTime - System.currentTimeMillis()) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            // Send 429 response
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
            // Take the first IP in the chain (original client)
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
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitEntry entry, long windowMs) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(config.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(entry.getRemainingRequests(config.getMaxRequests())));
        response.setHeader("X-RateLimit-Reset", String.valueOf(entry.getResetTime(windowMs) / 1000));
    }

    /**
     * Cleans up expired rate limit entries to prevent memory leaks.
     */
    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();

        if (now - lastCleanup.get() < CLEANUP_INTERVAL_MS) {
            return;
        }

        if (lastCleanup.compareAndSet(lastCleanup.get(), now)) {
            long windowMs = config.getWindowSeconds() * 1000L;
            long expirationThreshold = now - (windowMs * 2);

            rateLimitCache.entrySet().removeIf(entry ->
                entry.getValue().windowStart < expirationThreshold
            );

            log.debug("Rate limit cache cleanup completed. Remaining entries: {}", rateLimitCache.size());
        }
    }

    /**
     * Gets the current cache size (for monitoring).
     *
     * @return number of entries in the rate limit cache
     */
    public int getCacheSize() {
        return rateLimitCache.size();
    }
}
