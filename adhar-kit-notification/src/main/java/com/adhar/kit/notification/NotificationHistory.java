package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationHistoryEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory {@link NotificationHistoryStore} that tracks sent and failed notifications.
 * <p>
 * Uses a bounded {@link ConcurrentLinkedDeque} to store the most recent notification
 * history entries, automatically evicting the oldest entries when the maximum size is exceeded.
 * </p>
 * <p>
 * This implementation is thread-safe and suitable for concurrent access from multiple
 * notification channels. Durable stores (JDBC, JPA, etc.) can replace it by implementing
 * {@link NotificationHistoryStore} and registering the implementation as a Spring bean.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class NotificationHistory implements NotificationHistoryStore {

    private final ConcurrentLinkedDeque<NotificationHistoryEntry> entries = new ConcurrentLinkedDeque<>();
    private final int maxSize;

    /**
     * Creates a new notification history with the specified maximum size.
     *
     * @param maxSize the maximum number of history entries to retain
     */
    public NotificationHistory(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Records a notification send attempt in the history.
     *
     * @param notification the notification that was sent or attempted
     * @param success      whether the send was successful
     * @param channelType  the channel type used (e.g., "EMAIL", "SMS", "WEBHOOK")
     */
    @Override
    public void record(Notification notification, boolean success, String channelType) {
        record(notification, success, channelType, null);
    }

    /**
     * Records a notification send attempt in the history with an optional error message.
     *
     * @param notification the notification that was sent or attempted
     * @param success      whether the send was successful
     * @param channelType  the channel type used
     * @param errorMessage the error message if the send failed, or {@code null} on success
     */
    @Override
    public void record(Notification notification, boolean success, String channelType, String errorMessage) {
        var entry = new NotificationHistoryEntry(
                UUID.randomUUID().toString(),
                notification,
                success,
                channelType,
                Instant.now(),
                errorMessage
        );
        entries.addFirst(entry);
        trimToSize();
    }

    /**
     * Returns the most recent notification history entries, up to the specified limit.
     *
     * @param limit the maximum number of entries to return
     * @return an unmodifiable list of the most recent history entries
     */
    @Override
    public List<NotificationHistoryEntry> getHistory(int limit) {
        return entries.stream()
                .limit(limit)
                .toList();
    }

    /**
     * Returns all failed notification history entries.
     *
     * @return an unmodifiable list of failed notification entries
     */
    @Override
    public List<NotificationHistoryEntry> getFailedNotifications() {
        return entries.stream()
                .filter(entry -> !entry.success())
                .toList();
    }

    /**
     * Returns the total number of entries currently stored in the history.
     *
     * @return the history size
     */
    @Override
    public int size() {
        return entries.size();
    }

    /**
     * Clears all entries from the history.
     */
    @Override
    public void clear() {
        entries.clear();
    }

    private void trimToSize() {
        while (entries.size() > maxSize) {
            entries.pollLast();
        }
    }
}
