package com.adhar.kit.notification;

import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    private final List<NotificationChannel> channels;
    private final Executor executor;
    private final NotificationRetryHandler retryHandler;
    private final NotificationHistory history;

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
        this.channels = channels;
        this.executor = executor;
        this.retryHandler = retryHandler;
        this.history = history;
        log.info("DefaultNotificationService initialized with {} channel(s): {}, retry={}, history={}",
                channels.size(),
                channels.stream()
                        .map(ch -> ch.getClass().getSimpleName())
                        .toList(),
                retryHandler != null ? "enabled" : "disabled",
                history != null ? "enabled" : "disabled");
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
        } catch (Exception e) {
            log.warn("Initial send failed for notification [id={}, type={}]: {}",
                    notification.id(), notification.type(), e.getMessage());

            if (retryHandler != null) {
                boolean success = retryHandler.retry(notification, channel);
                recordHistory(notification, success, channelType,
                        success ? null : "All retry attempts exhausted: " + e.getMessage());
                if (!success) {
                    throw new RuntimeException(
                            "Failed to send notification [id=" + notification.id() + "] after retries", e);
                }
            } else {
                recordHistory(notification, false, channelType, e.getMessage());
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
}
