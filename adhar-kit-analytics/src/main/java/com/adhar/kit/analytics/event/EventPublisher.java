package com.adhar.kit.analytics.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing analytics events.
 * Sends events to Kafka for processing.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;
    private static final String ANALYTICS_TOPIC = "analytics-events";

    /**
     * Publish an analytics event.
     */
    public void publishEvent(AnalyticsEvent event) {
        try {
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now());
            }

            kafkaTemplate.send(ANALYTICS_TOPIC, event.getEventId(), event);
            log.debug("Published analytics event: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish analytics event", e);
        }
    }

    /**
     * Track a user action.
     */
    public void trackUserAction(String userId, String action, Map<String, Object> properties) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventType("user." + action)
                .userId(userId)
                .properties(properties)
                .category("user_action")
                .build();

        publishEvent(event);
    }

    /**
     * Track a business event.
     */
    public void trackBusinessEvent(String eventType, String userId, Map<String, Object> properties) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventType(eventType)
                .userId(userId)
                .properties(properties)
                .category("business")
                .build();

        publishEvent(event);
    }

    /**
     * Track a page view.
     */
    public void trackPageView(String userId, String sessionId, String page, Map<String, Object> properties) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventType("page.view")
                .userId(userId)
                .sessionId(sessionId)
                .properties(Map.of("page", page))
                .category("navigation")
                .build();

        if (properties != null) {
            event.getProperties().putAll(properties);
        }

        publishEvent(event);
    }
}

