package com.adhar.kit.security.audit;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Security audit logger for authentication events.
 *
 * <p>Listens to Spring Security authentication events and logs them in a structured format
 * suitable for security monitoring and compliance requirements.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Logs successful authentication events</li>
 *   <li>Logs failed authentication attempts</li>
 *   <li>Logs logout events</li>
 *   <li>Captures IP address and session information</li>
 *   <li>Structured JSON logging format</li>
 *   <li>Configurable event types</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   security:
 *     audit:
 *       enabled: true
 *       log-successful-auth: true
 *       log-failed-auth: true
 *       log-logout: true
 * }</pre>
 *
 * <p><b>Log Format:</b></p>
 * <pre>{@code
 * {
 *   "event": "AUTHENTICATION_SUCCESS",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "principal": "user@example.com",
 *   "ipAddress": "192.168.1.100",
 *   "sessionId": "ABC123...",
 *   "authorities": ["ROLE_USER"],
 *   "details": {}
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Component
@Slf4j
public class SecurityAuditLogger {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final org.slf4j.Logger AUDIT_LOGGER = org.slf4j.LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AdharSecurityProperties.AuditProperties config;

    /**
     * Security event types.
     */
    public enum SecurityEventType {
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILURE_BAD_CREDENTIALS,
        AUTHENTICATION_FAILURE_LOCKED,
        AUTHENTICATION_FAILURE_OTHER,
        LOGOUT_SUCCESS,
        SESSION_CREATED,
        SESSION_DESTROYED
    }

    /**
     * Creates security audit logger.
     *
     * @param config audit configuration properties
     */
    public SecurityAuditLogger(AdharSecurityProperties.AuditProperties config) {
        this.config = config;
        if (config.isEnabled()) {
            log.info("Security audit logging enabled - Success: {}, Failure: {}, Logout: {}",
                config.isLogSuccessfulAuth(), config.isLogFailedAuth(), config.isLogLogout());
        }
    }

    /**
     * Handles successful authentication events.
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (!config.isEnabled() || !config.isLogSuccessfulAuth()) {
            return;
        }

        logSecurityEvent(SecurityEventType.AUTHENTICATION_SUCCESS, event);
    }

    /**
     * Handles interactive authentication success (form login, etc.).
     */
    @EventListener
    public void onInteractiveAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
        if (!config.isEnabled() || !config.isLogSuccessfulAuth()) {
            return;
        }

        logSecurityEvent(SecurityEventType.AUTHENTICATION_SUCCESS, event);
    }

    /**
     * Handles failed authentication due to bad credentials.
     */
    @EventListener
    public void onAuthenticationFailureBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        if (!config.isEnabled() || !config.isLogFailedAuth()) {
            return;
        }

        logSecurityEvent(SecurityEventType.AUTHENTICATION_FAILURE_BAD_CREDENTIALS, event);
    }

    /**
     * Handles failed authentication due to locked account.
     */
    @EventListener
    public void onAuthenticationFailureLocked(AuthenticationFailureLockedEvent event) {
        if (!config.isEnabled() || !config.isLogFailedAuth()) {
            return;
        }

        logSecurityEvent(SecurityEventType.AUTHENTICATION_FAILURE_LOCKED, event);
    }

    /**
     * Handles logout success events.
     */
    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        if (!config.isEnabled() || !config.isLogLogout()) {
            return;
        }

        logSecurityEvent(SecurityEventType.LOGOUT_SUCCESS, event);
    }

    /**
     * Logs a security event with structured data.
     */
    private void logSecurityEvent(SecurityEventType eventType, AbstractAuthenticationEvent event) {
        try {
            Authentication auth = event.getAuthentication();
            Map<String, Object> auditData = new HashMap<>();

            auditData.put("event", eventType.name());
            auditData.put("timestamp", ISO_FORMATTER.format(Instant.now()));
            auditData.put("principal", extractPrincipalName(auth));

            // Extract IP address and session ID
            Object details = auth.getDetails();
            if (details instanceof WebAuthenticationDetails webDetails) {
                auditData.put("ipAddress", webDetails.getRemoteAddress());
                auditData.put("sessionId", maskSessionId(webDetails.getSessionId()));
            }

            // Extract authorities
            if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                auditData.put("authorities", auth.getAuthorities().stream()
                    .map(Object::toString)
                    .toList());
            }

            // Add event-specific details
            Map<String, Object> eventDetails = new HashMap<>();
            if (event instanceof AuthenticationFailureBadCredentialsEvent failureEvent) {
                eventDetails.put("reason", "bad_credentials");
                if (failureEvent.getException() != null) {
                    eventDetails.put("errorMessage", failureEvent.getException().getMessage());
                }
            } else if (event instanceof AuthenticationFailureLockedEvent lockedEvent) {
                eventDetails.put("reason", "account_locked");
                if (lockedEvent.getException() != null) {
                    eventDetails.put("errorMessage", lockedEvent.getException().getMessage());
                }
            }

            if (!eventDetails.isEmpty()) {
                auditData.put("details", eventDetails);
            }

            // Log to audit logger
            String auditJson = formatAsJson(auditData);

            if (isFailureEvent(eventType)) {
                AUDIT_LOGGER.warn("SECURITY_AUDIT: {}", auditJson);
            } else {
                AUDIT_LOGGER.info("SECURITY_AUDIT: {}", auditJson);
            }

        } catch (Exception e) {
            log.error("Failed to log security audit event", e);
        }
    }

    /**
     * Logs a custom security event.
     *
     * @param eventType type of security event
     * @param principal principal name
     * @param ipAddress client IP address
     * @param details additional details
     */
    public void logCustomEvent(SecurityEventType eventType, String principal,
                               String ipAddress, Map<String, Object> details) {
        if (!config.isEnabled()) {
            return;
        }

        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("event", eventType.name());
            auditData.put("timestamp", ISO_FORMATTER.format(Instant.now()));
            auditData.put("principal", principal);
            auditData.put("ipAddress", ipAddress);

            if (details != null && !details.isEmpty()) {
                auditData.put("details", details);
            }

            String auditJson = formatAsJson(auditData);

            if (isFailureEvent(eventType)) {
                AUDIT_LOGGER.warn("SECURITY_AUDIT: {}", auditJson);
            } else {
                AUDIT_LOGGER.info("SECURITY_AUDIT: {}", auditJson);
            }

        } catch (Exception e) {
            log.error("Failed to log custom security audit event", e);
        }
    }

    /**
     * Extracts the principal name from authentication.
     */
    private String extractPrincipalName(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return "anonymous";
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            return (String) principal;
        }

        // Try to get username from UserDetails
        try {
            return auth.getName();
        } catch (Exception e) {
            return principal.toString();
        }
    }

    /**
     * Masks session ID for security (shows first 8 characters).
     */
    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return sessionId;
        }
        return sessionId.substring(0, 8) + "***";
    }

    /**
     * Determines if the event type is a failure event.
     */
    private boolean isFailureEvent(SecurityEventType eventType) {
        return eventType.name().contains("FAILURE");
    }

    /**
     * Formats the audit data as JSON.
     */
    private String formatAsJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof java.util.List) {
                sb.append(formatListAsJson((java.util.List<?>) value));
            } else if (value instanceof Map) {
                sb.append(formatAsJson((Map<String, Object>) value));
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private String formatListAsJson(java.util.List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(item.toString())).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
