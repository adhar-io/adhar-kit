package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

/**
 * SMS notification channel stub.
 * <p>
 * This implementation logs the SMS notification details. Actual SMS provider integration
 * (e.g., Twilio, AWS SNS, Vonage) is left to consuming applications, which can provide
 * their own {@link NotificationChannel} bean that supports {@link NotificationType#SMS}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class SmsNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        String phoneNumber = notification.recipient();
        String provider = notification.metadata().getOrDefault("smsProvider", "default");

        log.info("SMS notification [id={}] to '{}' via provider '{}': subject='{}', body='{}'",
                notification.id(),
                phoneNumber,
                provider,
                notification.subject(),
                notification.body());
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.SMS;
    }
}
