package com.adhar.kit.notification.model;

import java.time.Instant;

/**
 * Record representing the outcome of a notification send attempt,
 * suitable for publishing as a CloudEvent payload.
 *
 * @param notificationId the unique notification identifier
 * @param type           the notification type (EMAIL, SMS, WEBHOOK, etc.)
 * @param recipient      the target recipient
 * @param success        whether the notification was sent successfully
 * @param errorMessage   the error message if the send failed, or {@code null} on success
 * @param timestamp      the time at which the outcome was recorded
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record NotificationEvent(
        String notificationId,
        String type,
        String recipient,
        boolean success,
        String errorMessage,
        Instant timestamp
) {

    /**
     * Creates a success notification event.
     *
     * @param notificationId the notification identifier
     * @param type           the notification type
     * @param recipient      the recipient
     * @return a new success {@code NotificationEvent}
     */
    public static NotificationEvent success(String notificationId, String type, String recipient) {
        return new NotificationEvent(notificationId, type, recipient, true, null, Instant.now());
    }

    /**
     * Creates a failure notification event.
     *
     * @param notificationId the notification identifier
     * @param type           the notification type
     * @param recipient      the recipient
     * @param errorMessage   the error description
     * @return a new failure {@code NotificationEvent}
     */
    public static NotificationEvent failure(String notificationId, String type, String recipient, String errorMessage) {
        return new NotificationEvent(notificationId, type, recipient, false, errorMessage, Instant.now());
    }
}
