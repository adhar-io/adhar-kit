package com.adhar.kit.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Default {@link AuditEventSink} that serializes events to JSON with Jackson and writes
 * them to the dedicated {@code SECURITY_AUDIT} SLF4J logger (WARN for failure events,
 * INFO otherwise).
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class Slf4jAuditEventSink implements AuditEventSink {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final ObjectMapper objectMapper;

    /**
     * Creates the sink with a private default {@link ObjectMapper}.
     */
    public Slf4jAuditEventSink() {
        this(new ObjectMapper());
    }

    /**
     * Creates the sink with the given {@link ObjectMapper}.
     *
     * @param objectMapper mapper used to serialize audit data
     */
    public Slf4jAuditEventSink(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(SecurityAuditLogger.SecurityEventType eventType, Map<String, Object> auditData) {
        try {
            String auditJson = objectMapper.writeValueAsString(auditData);
            if (isFailureEvent(eventType)) {
                AUDIT_LOGGER.warn("SECURITY_AUDIT: {}", auditJson);
            } else {
                AUDIT_LOGGER.info("SECURITY_AUDIT: {}", auditJson);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize security audit event", e);
        }
    }

    private boolean isFailureEvent(SecurityAuditLogger.SecurityEventType eventType) {
        return eventType.name().contains("FAILURE");
    }
}
