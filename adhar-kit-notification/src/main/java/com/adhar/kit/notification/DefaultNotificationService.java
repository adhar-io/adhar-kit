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
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DefaultNotificationService implements NotificationService {

    private final List<NotificationChannel> channels;
    private final Executor executor;

    /**
     * Creates a new DefaultNotificationService.
     *
     * @param channels the list of available notification channels
     * @param executor the executor for async operations
     */
    public DefaultNotificationService(List<NotificationChannel> channels, Executor executor) {
        this.channels = channels;
        this.executor = executor;
        log.info("DefaultNotificationService initialized with {} channel(s): {}",
                channels.size(),
                channels.stream()
                        .map(ch -> ch.getClass().getSimpleName())
                        .toList());
    }

    @Override
    public void send(Notification notification) {
        var channel = findChannel(notification);
        log.debug("Routing notification [id={}, type={}] to {}",
                notification.id(), notification.type(), channel.getClass().getSimpleName());
        channel.send(notification);
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
}
