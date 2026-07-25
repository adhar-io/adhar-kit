package com.adhar.kit.notification;

import com.adhar.kit.notification.model.NotificationType;

import java.util.Set;

/**
 * SPI for per-recipient notification channel preferences (opt-outs).
 * <p>
 * A recipient may opt out of individual notification channels; opted-out sends are
 * silently skipped by {@link DefaultNotificationService}. The default implementation is
 * {@link InMemoryNotificationPreferenceStore}; applications can provide a persistent
 * implementation as a Spring bean, which replaces the in-memory default via
 * {@code @ConditionalOnMissingBean}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface NotificationPreferenceStore {

    /**
     * Opts a recipient out of receiving notifications through the given channel type.
     *
     * @param recipient the recipient identifier (email address, phone number, user id, etc.)
     * @param type      the notification channel type to opt out of
     */
    void optOut(String recipient, NotificationType type);

    /**
     * Removes an opt-out, re-enabling notifications for the recipient on the given channel type.
     *
     * @param recipient the recipient identifier
     * @param type      the notification channel type to opt back into
     */
    void optIn(String recipient, NotificationType type);

    /**
     * Returns whether the recipient has opted out of the given channel type.
     *
     * @param recipient the recipient identifier
     * @param type      the notification channel type
     * @return {@code true} if the recipient opted out of the channel
     */
    boolean isOptedOut(String recipient, NotificationType type);

    /**
     * Returns all channel types the recipient has opted out of.
     *
     * @param recipient the recipient identifier
     * @return an unmodifiable set of opted-out channel types (empty if none)
     */
    Set<NotificationType> getOptOuts(String recipient);
}
