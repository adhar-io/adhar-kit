package com.adhar.kit.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base analytics event for tracking user actions and system events.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent {

    /**
     * Unique event identifier.
     */
    private String eventId;

    /**
     * Event type (e.g., "user.login", "order.created", "api.call").
     */
    private String eventType;

    /**
     * Event category (e.g., "user", "order", "api").
     */
    private String category;

    /**
     * Event timestamp.
     */
    private LocalDateTime timestamp;

    /**
     * User ID who triggered the event.
     */
    private String userId;

    /**
     * Session ID.
     */
    private String sessionId;

    /**
     * Source of the event (e.g., "controller", "mobile", "api").
     */
    private String source;

    /**
     * Event properties (custom key-value pairs).
     */
    private Map<String, Object> properties;

    /**
     * Event metadata.
     */
    private Map<String, String> metadata;

    /**
     * IP address of the client.
     */
    private String ipAddress;

    /**
     * User agent string.
     */
    private String userAgent;

    /**
     * Whether the event is critical.
     */
    private boolean critical = false;
}

