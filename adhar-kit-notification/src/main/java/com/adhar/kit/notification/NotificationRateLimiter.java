package com.adhar.kit.notification;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.NotificationType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory sliding-window rate limiter for notification sends, keyed by
 * recipient and channel type.
 * <p>
 * Each recipient/channel pair may send at most
 * {@code adhar.notification.rate-limit.max-per-window} notifications within the
 * configured {@code window-ms} sliding window. Send timestamps are tracked per key
 * and expired lazily as new permits are requested.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class NotificationRateLimiter {

    private final int maxPerWindow;
    private final long windowMs;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * Creates a rate limiter from the configured rate-limit properties using the system clock.
     *
     * @param properties the rate limit configuration
     */
    public NotificationRateLimiter(NotificationProperties.RateLimitProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * Creates a rate limiter with an explicit clock, primarily for testing.
     *
     * @param properties the rate limit configuration
     * @param clock      supplier of the current time in milliseconds
     */
    public NotificationRateLimiter(NotificationProperties.RateLimitProperties properties, LongSupplier clock) {
        this.maxPerWindow = properties.getMaxPerWindow();
        this.windowMs = properties.getWindowMs();
        this.clock = clock;
    }

    /**
     * Attempts to acquire a send permit for the given recipient and channel type.
     *
     * @param recipient the recipient identifier
     * @param type      the notification channel type
     * @return {@code true} if the send is within the rate limit, {@code false} if the
     *         recipient/channel pair has exhausted its window quota
     */
    public boolean tryAcquire(String recipient, NotificationType type) {
        String key = recipient + '|' + type.name();
        Deque<Long> timestamps = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long now = clock.getAsLong();
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerWindow) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Returns the number of permits currently consumed within the window for the
     * given recipient and channel type.
     *
     * @param recipient the recipient identifier
     * @param type      the notification channel type
     * @return the number of sends recorded in the current window
     */
    public int currentCount(String recipient, NotificationType type) {
        Deque<Long> timestamps = windows.get(recipient + '|' + type.name());
        if (timestamps == null) {
            return 0;
        }
        synchronized (timestamps) {
            long now = clock.getAsLong();
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMs) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }

    /**
     * Clears all tracked windows.
     */
    public void reset() {
        windows.clear();
    }
}
