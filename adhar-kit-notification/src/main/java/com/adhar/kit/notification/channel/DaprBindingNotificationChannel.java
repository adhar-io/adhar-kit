package com.adhar.kit.notification.channel;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Notification channel delivering through Dapr output bindings, so the actual
 * provider (SMTP server, Twilio, SendGrid, HTTP endpoint, ...) is whatever
 * binding components the sidecar is configured with - no provider SDK on the
 * application classpath.
 *
 * <p>Type mapping (per the Dapr binding specs):</p>
 * <ul>
 *   <li>{@link NotificationType#EMAIL} - operation {@code create} on the
 *       configured email binding (e.g. an SMTP or SendGrid component) with
 *       {@code emailTo}/{@code subject} metadata and the body as payload.</li>
 *   <li>{@link NotificationType#SMS} - operation {@code create} on the SMS
 *       binding (e.g. Twilio) with {@code toNumber} metadata.</li>
 *   <li>{@link NotificationType#WEBHOOK} - operation {@code post} on the HTTP
 *       binding. Note the target URL is fixed by the binding component; the
 *       notification's recipient is passed as {@code path} metadata for
 *       components that support per-request paths.</li>
 * </ul>
 *
 * <p>Binding failures propagate as exceptions so the notification service
 * records the delivery as failed (and retries per its policy) instead of
 * silently succeeding. Notification metadata entries are forwarded as binding
 * metadata and can override the defaults above.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public final class DaprBindingNotificationChannel implements NotificationChannel {

    private final DaprFacade daprFacade;
    private final NotificationProperties.DaprProperties config;

    /**
     * Creates the channel.
     *
     * @param daprFacade the Dapr facade used for binding invocations
     * @param config     binding names and per-type enablement
     */
    public DaprBindingNotificationChannel(DaprFacade daprFacade,
                                          NotificationProperties.DaprProperties config) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public boolean supports(NotificationType type) {
        return switch (type) {
            case EMAIL -> config.isEmailEnabled() && hasText(config.getEmailBinding());
            case SMS -> config.isSmsEnabled() && hasText(config.getSmsBinding());
            case WEBHOOK -> config.isWebhookEnabled() && hasText(config.getHttpBinding());
            case IN_APP -> false;
        };
    }

    @Override
    public void send(Notification notification) {
        switch (notification.type()) {
            case EMAIL -> {
                Map<String, String> metadata = baseMetadata(notification);
                metadata.putIfAbsent("emailTo", notification.recipient());
                if (notification.subject() != null) {
                    metadata.putIfAbsent("subject", notification.subject());
                }
                invoke(config.getEmailBinding(), "create", notification, metadata);
            }
            case SMS -> {
                Map<String, String> metadata = baseMetadata(notification);
                metadata.putIfAbsent("toNumber", notification.recipient());
                invoke(config.getSmsBinding(), "create", notification, metadata);
            }
            case WEBHOOK -> {
                Map<String, String> metadata = baseMetadata(notification);
                if (hasText(notification.recipient())) {
                    metadata.putIfAbsent("path", notification.recipient());
                }
                invoke(config.getHttpBinding(), "post", notification, metadata);
            }
            case IN_APP -> throw new UnsupportedOperationException(
                    "IN_APP notifications are not delivered via Dapr bindings");
        }
    }

    private void invoke(String binding, String operation, Notification notification,
                        Map<String, String> metadata) {
        log.debug("Sending {} notification [id={}] via Dapr binding '{}'",
                notification.type(), notification.id(), binding);
        daprFacade.invokeBinding(binding, operation, notification.body(), metadata);
    }

    private Map<String, String> baseMetadata(Notification notification) {
        return new HashMap<>(notification.metadata());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
