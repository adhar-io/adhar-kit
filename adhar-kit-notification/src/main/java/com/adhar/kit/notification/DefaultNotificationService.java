package com.adhar.kit.notification;

import com.adhar.kit.commons.event.AdharCloudEvent;
import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Default implementation of {@link NotificationService} that routes notifications
 * to the appropriate {@link NotificationChannel} based on the notification type.
 * <p>
 * Integrates with {@link NotificationRetryHandler} for automatic retry on failure
 * and {@link NotificationHistory} for tracking send attempts.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DefaultNotificationService implements NotificationService {

    private static final String CLOUD_EVENT_SOURCE = "adhar-kit/notification";
    private static final String CLOUD_EVENT_TYPE_SENT = "com.adhar.notification.sent";
    private static final String CLOUD_EVENT_TYPE_FAILED = "com.adhar.notification.failed";

    private final List<NotificationChannel> channels;
    private final Executor executor;
    private final NotificationRetryHandler retryHandler;
    private final NotificationHistory history;
    private final Consumer<AdharCloudEvent<?>> eventPublisher;

    /**
     * Creates a new DefaultNotificationService with retry and history support.
     *
     * @param channels     the list of available notification channels
     * @param executor     the executor for async operations
     * @param retryHandler the retry handler for failed sends (may be {@code null})
     * @param history      the notification history recorder (may be {@code null})
     */
    public DefaultNotificationService(List<NotificationChannel> channels, Executor executor,
                                      NotificationRetryHandler retryHandler, NotificationHistory history) {
        this(channels, executor, retryHandler, history, null);
    }

    /**
     * Creates a new DefaultNotificationService with retry, history, and CloudEvent publishing support.
     *
     * @param channels       the list of available notification channels
     * @param executor       the executor for async operations
     * @param retryHandler   the retry handler for failed sends (may be {@code null})
     * @param history        the notification history recorder (may be {@code null})
     * @param eventPublisher optional consumer that receives CloudEvents after each send attempt (may be {@code null})
     */
    public DefaultNotificationService(List<NotificationChannel> channels, Executor executor,
                                      NotificationRetryHandler retryHandler, NotificationHistory history,
                                      Consumer<AdharCloudEvent<?>> eventPublisher) {
        this.channels = channels;
        this.executor = executor;
        this.retryHandler = retryHandler;
        this.history = history;
        this.eventPublisher = eventPublisher;
        log.info("DefaultNotificationService initialized with {} channel(s): {}, retry={}, history={}, cloudEvents={}",
                channels.size(),
                channels.stream()
                        .map(ch -> ch.getClass().getSimpleName())
                        .toList(),
                retryHandler != null ? "enabled" : "disabled",
                history != null ? "enabled" : "disabled",
                eventPublisher != null ? "enabled" : "disabled");
    }

    @Override
    public void send(Notification notification) {
        var channel = findChannel(notification);
        String channelType = notification.type().name();

        log.debug("Routing notification [id={}, type={}] to {}",
                notification.id(), notification.type(), channel.getClass().getSimpleName());

        try {
            channel.send(notification);
            recordHistory(notification, true, channelType, null);
            publishCloudEvent(NotificationEvent.success(notification.id(), channelType, notification.recipient()),
                    CLOUD_EVENT_TYPE_SENT);
        } catch (Exception e) {
            log.warn("Initial send failed for notification [id={}, type={}]: {}",
                    notification.id(), notification.type(), e.getMessage());

            if (retryHandler != null) {
                boolean success = retryHandler.retry(notification, channel);
                recordHistory(notification, success, channelType,
                        success ? null : "All retry attempts exhausted: " + e.getMessage());
                if (success) {
                    publishCloudEvent(NotificationEvent.success(notification.id(), channelType, notification.recipient()),
                            CLOUD_EVENT_TYPE_SENT);
                } else {
                    publishCloudEvent(NotificationEvent.failure(notification.id(), channelType, notification.recipient(),
                            "All retry attempts exhausted: " + e.getMessage()), CLOUD_EVENT_TYPE_FAILED);
                    throw new RuntimeException(
                            "Failed to send notification [id=" + notification.id() + "] after retries", e);
                }
            } else {
                recordHistory(notification, false, channelType, e.getMessage());
                publishCloudEvent(NotificationEvent.failure(notification.id(), channelType, notification.recipient(),
                        e.getMessage()), CLOUD_EVENT_TYPE_FAILED);
                throw e;
            }
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(Notification notification) {
        return CompletableFuture.runAsync(() -> send(notification), executor);
    }

    @Override
    public void sendBatch(List<Notification> notifications) {
        log.debug("Sending batch of {} notification(s)", notifications.size());
        for (var notification : notifications) {
            try {
                send(notification);
            } catch (Exception e) {
                log.error("Failed to send notification [id={}, type={}]: {}",
                        notification.id(), notification.type(), e.getMessage(), e);
            }
        }
    }

    private NotificationChannel findChannel(Notification notification) {
        return channels.stream()
                .filter(ch -> ch.supports(notification.type()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No notification channel found for type: " + notification.type()));
    }

    private void recordHistory(Notification notification, boolean success, String channelType, String errorMessage) {
        if (history != null) {
            history.record(notification, success, channelType, errorMessage);
        }
    }

    private void publishCloudEvent(NotificationEvent notificationEvent, String type) {
        if (eventPublisher != null) {
            try {
                AdharCloudEvent<NotificationEvent> cloudEvent = AdharCloudEvent.of(
                        CLOUD_EVENT_SOURCE,
                        type,
                        notificationEvent.notificationId(),
                        notificationEvent
                );
                eventPublisher.accept(cloudEvent);
                log.debug("Published CloudEvent [type={}, subject={}]", type, notificationEvent.notificationId());
            } catch (Exception e) {
                log.warn("Failed to publish CloudEvent for notification [id={}]: {}",
                        notificationEvent.notificationId(), e.getMessage());
            }
        }
    }
}
