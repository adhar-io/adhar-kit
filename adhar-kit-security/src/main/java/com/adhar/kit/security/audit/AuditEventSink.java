package com.adhar.kit.security.audit;

import java.util.Map;

/**
 * Destination for security audit events produced by {@link SecurityAuditLogger}.
 *
 * <p>The default implementation ({@link Slf4jAuditEventSink}) writes JSON to the
 * {@code SECURITY_AUDIT} logger. Provide a custom bean of this type to ship audit
 * events elsewhere (Kafka, SIEM, database, ...).</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@FunctionalInterface
public interface AuditEventSink {

    /**
     * Publishes a single audit event.
     *
     * @param eventType the type of security event
     * @param auditData structured event attributes (event, timestamp, principal,
     *        ipAddress, authorities, details, ...); values are JSON-friendly types
     *        (String, Number, Boolean, List, Map)
     */
    void publish(SecurityAuditLogger.SecurityEventType eventType, Map<String, Object> auditData);
}
