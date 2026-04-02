package com.adhar.kit.notification.model;

import java.time.Instant;

/**
 * Immutable record representing a single entry in the notification history.
 *
 * @param id          unique identifier for this history entry
 * @param notification the notification that was sent (or attempted)
 * @param success     whether the notification was delivered successfully
 * @param channelType the channel type used for delivery (e.g., "EMAIL", "SMS")
 * @param timestamp   the time at which the send attempt occurred
 * @param errorMessage the error message if the send failed, or {@code null} on success
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record NotificationHistoryEntry(
        String id,
        Notification notification,
        boolean success,
        String channelType,
        Instant timestamp,
        String errorMessage
) {

    /**
     * Compact constructor that provides defaults for null fields.
     */
    public NotificationHistoryEntry {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
