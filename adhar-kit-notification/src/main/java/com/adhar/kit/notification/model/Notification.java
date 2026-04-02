package com.adhar.kit.notification.model;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable notification record representing a notification to be sent through a channel.
 *
 * @param id        unique identifier for the notification
 * @param type      the notification type determining which channel to use
 * @param recipient the target recipient (email address, webhook URL, user ID, etc.)
 * @param subject   the notification subject or title
 * @param body      the notification body or content
 * @param metadata  additional key-value metadata for channel-specific configuration
 * @param createdAt the timestamp when the notification was created
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record Notification(
        String id,
        NotificationType type,
        String recipient,
        String subject,
        String body,
        Map<String, String> metadata,
        Instant createdAt
) {

    /**
     * Creates a Notification with sensible defaults for metadata and createdAt.
     */
    public Notification {
        if (metadata == null) {
            metadata = Map.of();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
