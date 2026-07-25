package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationHistoryEntry;

import java.util.List;

/**
 * SPI for notification history persistence.
 * <p>
 * Implementations record notification send attempts and expose query methods over the
 * stored entries. The default implementation is the in-memory {@link NotificationHistory};
 * applications requiring durable history (JDBC, JPA, etc.) can provide their own
 * implementation as a Spring bean, which replaces the in-memory default via
 * {@code @ConditionalOnMissingBean}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface NotificationHistoryStore {

    /**
     * Records a notification send attempt with an optional error message.
     *
     * @param notification the notification that was sent or attempted
     * @param success      whether the send was successful
     * @param channelType  the channel type used (e.g., "EMAIL", "SMS", "WEBHOOK")
     * @param errorMessage the error message if the send failed, or {@code null} on success
     */
    void record(Notification notification, boolean success, String channelType, String errorMessage);

    /**
     * Records a successful or failed notification send attempt without an error message.
     *
     * @param notification the notification that was sent or attempted
     * @param success      whether the send was successful
     * @param channelType  the channel type used
     */
    default void record(Notification notification, boolean success, String channelType) {
        record(notification, success, channelType, null);
    }

    /**
     * Returns the most recent notification history entries, up to the specified limit.
     *
     * @param limit the maximum number of entries to return
     * @return an unmodifiable list of the most recent history entries
     */
    List<NotificationHistoryEntry> getHistory(int limit);

    /**
     * Returns all failed notification history entries.
     *
     * @return an unmodifiable list of failed notification entries
     */
    List<NotificationHistoryEntry> getFailedNotifications();

    /**
     * Returns the total number of entries currently stored.
     *
     * @return the history size
     */
    int size();

    /**
     * Clears all entries from the history.
     */
    void clear();
}
