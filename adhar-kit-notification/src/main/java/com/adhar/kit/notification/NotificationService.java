package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for sending notifications through configured channels.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * Sends a notification synchronously through the appropriate channel.
     *
     * @param notification the notification to send
     */
    void send(Notification notification);

    /**
     * Sends a notification asynchronously through the appropriate channel.
     *
     * @param notification the notification to send
     * @return a CompletableFuture that completes when the notification is sent
     */
    CompletableFuture<Void> sendAsync(Notification notification);

    /**
     * Sends a batch of notifications. Each notification is routed to the appropriate
     * channel based on its type.
     *
     * @param notifications the list of notifications to send
     */
    void sendBatch(List<Notification> notifications);
}
