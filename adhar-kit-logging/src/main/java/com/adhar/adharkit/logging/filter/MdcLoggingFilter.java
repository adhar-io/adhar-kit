package com.adhar.adharkit.logging.filter;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds MDC context information to each HTTP request.
 * <p>
 * This filter automatically adds the following context information to the MDC:
 * <ul>
 *   <li>Correlation ID - for tracking requests across services</li>
 *   <li>User information - from the security context if available</li>
 *   <li>Request information - method, URI, client IP, user agent</li>
 *   <li>Trace and span IDs - from distributed tracing if available</li>
 * </ul>
 * <p>
 * The filter runs with the highest precedence to ensure MDC context is available
 * for all subsequent filters and request processing.
 */
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    // Standard HTTP headers for correlation ID
    private static final String[] CORRELATION_ID_HEADERS = {
        "X-Correlation-ID", "X-Request-ID", "X-Trace-ID", "correlation-id", "request-id"
    };

    private final AdharLoggingProperties properties;
    private final AdharLogger adharLogger;

    /**
     * Constructor.
     *
     * @param properties the logging properties
     * @param adharLogger the Adhar logger instance
     */
    public MdcLoggingFilter(AdharLoggingProperties properties, AdharLogger adharLogger) {
        this.properties = properties;
        this.adharLogger = adharLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.getMdc().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Set correlation ID from request header or generate a new one
            String correlationId = extractCorrelationId(request);
            String actualCorrelationId = adharLogger.setCorrelationId(correlationId);

            // Add correlation ID to response header for downstream services
            if (StringUtils.hasText(actualCorrelationId)) {
                response.setHeader(properties.getMdc().getCorrelationIdField(), actualCorrelationId);
            }

            // Add user information if available and enabled
            if (properties.getMdc().isIncludeUserInfo()) {
                addUserContext();
            }

            // Add request context information
            addRequestContext(request);

            // Add tracing information if available
            adharLogger.setTracingInfo();

            log.debug("MDC context set for request: {} {}", request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

        } finally {
            // Clear MDC to prevent memory leaks and context pollution
            MDC.clear();
            log.trace("MDC context cleared after request processing");
        }
    }

    /**
     * Extract correlation ID from request headers.
     * Tries multiple common header names for correlation ID.
     */
    private String extractCorrelationId(HttpServletRequest request) {
        for (String headerName : CORRELATION_ID_HEADERS) {
            String correlationId = request.getHeader(headerName);
            if (StringUtils.hasText(correlationId)) {
                log.debug("Found correlation ID '{}' in header '{}'", correlationId, headerName);
                return correlationId;
            }
        }

        // Generate new correlation ID if none found
        String newCorrelationId = UUID.randomUUID().toString();
        log.debug("Generated new correlation ID: {}", newCorrelationId);
        return newCorrelationId;
    }

    /**
     * Add user context information to MDC from Spring Security context.
     */
    private void addUserContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                if (StringUtils.hasText(username) && !"anonymousUser".equals(username)) {
                    adharLogger.setUserId(username);

                    // Add additional user context
                    adharLogger.put("userAuthenticated", "true");
                    if (!authentication.getAuthorities().isEmpty()) {
                        adharLogger.put("userRoles", authentication.getAuthorities().toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user context: {}", e.getMessage());
        }
    }

    /**
     * Add request context information to MDC.
     */
    private void addRequestContext(HttpServletRequest request) {
        try {
            // Basic request information
            adharLogger.put("httpMethod", request.getMethod());
            adharLogger.put("requestUri", request.getRequestURI());

            // Client information
            String remoteAddr = getClientIpAddress(request);
            if (StringUtils.hasText(remoteAddr)) {
                adharLogger.put("clientIp", remoteAddr);
            }

            String userAgent = request.getHeader("User-Agent");
            if (StringUtils.hasText(userAgent)) {
                adharLogger.put("userAgent", userAgent);
            }

            // Session information
            if (request.getSession(false) != null) {
                adharLogger.put("sessionId", request.getSession().getId());
            }

            // Request ID for this specific request
            adharLogger.put("requestId", UUID.randomUUID().toString());

        } catch (Exception e) {
            log.warn("Failed to extract request context: {}", e.getMessage());
        }
    }

    /**
     * Get the real client IP address, considering proxy headers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For", "X-Real-IP", "X-Client-IP",
            "CF-Connecting-IP", "True-Client-IP"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, get the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filtering for static resources and health checks
        String requestURI = request.getRequestURI();
        return requestURI.startsWith("/static/") ||
               requestURI.startsWith("/webjars/") ||
               requestURI.startsWith("/favicon.ico") ||
               requestURI.startsWith("/actuator/health") ||
               requestURI.startsWith("/health");
    }
}
