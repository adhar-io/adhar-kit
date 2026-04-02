package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

/**
 * In-application notification channel.
 * <p>
 * Logs and stores in-app notifications. This default implementation logs the notification;
 * applications can extend the behaviour by providing a custom {@link NotificationChannel}
 * bean that supports {@link NotificationType#IN_APP}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class InAppNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        log.info("In-app notification [id={}] delivered to user '{}': subject='{}', body='{}'",
                notification.id(),
                notification.recipient(),
                notification.subject(),
                notification.body());
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.IN_APP;
    }
}
