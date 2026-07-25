package com.adhar.kit.notification;

/**
 * SPI for idempotency-key duplicate suppression.
 * <p>
 * When a notification carries an idempotency key (metadata key
 * {@code idempotencyKey}), {@link DefaultNotificationService} registers the key before
 * sending; a duplicate key within the store's TTL causes the send to be skipped.
 * The default implementation is {@link InMemoryNotificationIdempotencyStore};
 * applications can provide a distributed implementation (e.g., Redis-backed) as a
 * Spring bean, which replaces the default via {@code @ConditionalOnMissingBean}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface NotificationIdempotencyStore {

    /**
     * Registers an idempotency key, reporting whether it was seen for the first time
     * within the store's TTL.
     *
     * @param key the idempotency key
     * @return {@code true} if the key is new (send should proceed), {@code false} if the
     *         key was already registered within the TTL (duplicate; send should be skipped)
     */
    boolean register(String key);
}
