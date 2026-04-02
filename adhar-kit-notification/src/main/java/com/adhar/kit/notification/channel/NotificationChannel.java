package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;

/**
 * Sealed interface for notification channels.
 * <p>
 * Each implementation handles a specific {@link NotificationType} and is responsible
 * for delivering notifications through its respective transport mechanism.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public sealed interface NotificationChannel
        permits EmailNotificationChannel, WebhookNotificationChannel, InAppNotificationChannel {

    /**
     * Sends a notification through this channel.
     *
     * @param notification the notification to send
     */
    void send(Notification notification);

    /**
     * Returns whether this channel supports the given notification type.
     *
     * @param type the notification type to check
     * @return {@code true} if this channel can handle the given type
     */
    boolean supports(NotificationType type);
}
