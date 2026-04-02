package com.adhar.kit.notification;

import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles retry logic for failed notification sends with exponential backoff.
 * <p>
 * When a notification send fails, this handler retries up to a configurable maximum
 * number of attempts. Each retry waits for an exponentially increasing backoff period
 * (base delay * 2^attemptIndex).
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class NotificationRetryHandler {

    private final int maxRetries;
    private final long backoffMs;

    /**
     * Creates a new retry handler with configuration from properties.
     *
     * @param properties the notification configuration properties
     */
    public NotificationRetryHandler(NotificationProperties properties) {
        this.maxRetries = properties.getRetry().getMaxRetries();
        this.backoffMs = properties.getRetry().getBackoffMs();
    }

    /**
     * Attempts to send a notification through the given channel with retries on failure.
     * <p>
     * Uses exponential backoff between retry attempts. Each attempt is logged at WARN level
     * if it fails, with the final failure logged at ERROR level.
     * </p>
     *
     * @param notification the notification to send
     * @param channel      the channel to send through
     * @param maxRetries   the maximum number of retry attempts (overrides configured default)
     * @return {@code true} if the notification was sent successfully, {@code false} if all retries exhausted
     */
    public boolean retry(Notification notification, NotificationChannel channel, int maxRetries) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = backoffMs * (1L << (attempt - 1));
                    log.warn("Retry attempt {}/{} for notification [id={}, type={}] after {}ms backoff",
                            attempt, maxRetries, notification.id(), notification.type(), delay);
                    Thread.sleep(delay);
                }
                channel.send(notification);
                if (attempt > 0) {
                    log.info("Notification [id={}] sent successfully on retry attempt {}/{}",
                            notification.id(), attempt, maxRetries);
                }
                return true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Retry interrupted for notification [id={}]", notification.id());
                return false;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("All {} retry attempts exhausted for notification [id={}, type={}]: {}",
                            maxRetries, notification.id(), notification.type(), e.getMessage(), e);
                } else {
                    log.warn("Attempt {}/{} failed for notification [id={}, type={}]: {}",
                            attempt, maxRetries, notification.id(), notification.type(), e.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Attempts to send a notification using the configured default max retries.
     *
     * @param notification the notification to send
     * @param channel      the channel to send through
     * @return {@code true} if the notification was sent successfully
     */
    public boolean retry(Notification notification, NotificationChannel channel) {
        return retry(notification, channel, this.maxRetries);
    }
}
