package com.adhar.kit.notification;

import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles retry logic for failed notification sends with exponential backoff.
 * <p>
 * When a notification send fails, this handler schedules retry attempts on a
 * {@link ScheduledExecutorService} instead of blocking the calling thread with
 * {@code Thread.sleep}. Each retry waits for an exponentially increasing backoff
 * period (base delay * 2^attemptIndex), capped at the configured maximum backoff.
 * </p>
 * <p>
 * The asynchronous entry point is {@link #retryAsync(Notification, NotificationChannel, int)},
 * which returns a {@link CompletableFuture} completing with the final outcome. The
 * synchronous {@link #retry(Notification, NotificationChannel, int)} methods are retained
 * for callers that need a blocking result; they simply wait on the scheduled pipeline.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class NotificationRetryHandler implements AutoCloseable {

    private final int maxRetries;
    private final long backoffMs;
    private final long maxBackoffMs;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    /**
     * Creates a new retry handler with configuration from properties and an internally
     * managed single-threaded daemon scheduler.
     *
     * @param properties the notification configuration properties
     */
    public NotificationRetryHandler(NotificationProperties properties) {
        this(properties, defaultScheduler(), true);
    }

    /**
     * Creates a new retry handler using the supplied scheduler.
     * <p>
     * The caller retains ownership of the scheduler; {@link #close()} will not shut it down.
     * </p>
     *
     * @param properties the notification configuration properties
     * @param scheduler  the scheduler used for backoff delays
     */
    public NotificationRetryHandler(NotificationProperties properties, ScheduledExecutorService scheduler) {
        this(properties, scheduler, false);
    }

    private NotificationRetryHandler(NotificationProperties properties, ScheduledExecutorService scheduler,
                                     boolean ownsScheduler) {
        this.maxRetries = properties.getRetry().getMaxRetries();
        this.backoffMs = properties.getRetry().getBackoffMs();
        this.maxBackoffMs = properties.getRetry().getMaxBackoffMs();
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    private static ScheduledExecutorService defaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-retry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Attempts to send a notification, scheduling retries with exponential backoff on failure.
     * <p>
     * The first attempt is executed on the calling thread; subsequent attempts run on the
     * scheduler after their backoff delay. The returned future never completes exceptionally:
     * it completes with {@code false} when all retries are exhausted.
     * </p>
     *
     * @param notification the notification to send
     * @param channel      the channel to send through
     * @param maxRetries   the maximum number of retry attempts (overrides configured default)
     * @return a future completing with {@code true} on success, {@code false} when retries are exhausted
     */
    public CompletableFuture<Boolean> retryAsync(Notification notification, NotificationChannel channel,
                                                 int maxRetries) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        attempt(notification, channel, maxRetries, 0, result);
        return result;
    }

    /**
     * Attempts to send a notification asynchronously using the configured default max retries.
     *
     * @param notification the notification to send
     * @param channel      the channel to send through
     * @return a future completing with {@code true} on success, {@code false} when retries are exhausted
     */
    public CompletableFuture<Boolean> retryAsync(Notification notification, NotificationChannel channel) {
        return retryAsync(notification, channel, this.maxRetries);
    }

    /**
     * Attempts to send a notification with retries, blocking until the outcome is known.
     * <p>
     * Backoff delays are served by the scheduler (no {@code Thread.sleep} in a loop);
     * the calling thread merely awaits the result.
     * </p>
     *
     * @param notification the notification to send
     * @param channel      the channel to send through
     * @param maxRetries   the maximum number of retry attempts (overrides configured default)
     * @return {@code true} if the notification was sent successfully, {@code false} if all retries exhausted
     */
    public boolean retry(Notification notification, NotificationChannel channel, int maxRetries) {
        return retryAsync(notification, channel, maxRetries).join();
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

    private void attempt(Notification notification, NotificationChannel channel, int maxRetries,
                         int attempt, CompletableFuture<Boolean> result) {
        try {
            channel.send(notification);
            if (attempt > 0) {
                log.info("Notification [id={}] sent successfully on retry attempt {}/{}",
                        notification.id(), attempt, maxRetries);
            }
            result.complete(true);
        } catch (Exception e) {
            if (attempt >= maxRetries) {
                log.error("All {} retry attempts exhausted for notification [id={}, type={}]: {}",
                        maxRetries, notification.id(), notification.type(), e.getMessage(), e);
                result.complete(false);
                return;
            }
            long delay = nextDelay(attempt);
            log.warn("Attempt {}/{} failed for notification [id={}, type={}]: {} - retrying in {}ms",
                    attempt, maxRetries, notification.id(), notification.type(), e.getMessage(), delay);
            try {
                scheduler.schedule(() -> attempt(notification, channel, maxRetries, attempt + 1, result),
                        delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rejected) {
                log.error("Retry scheduling rejected for notification [id={}]: {}",
                        notification.id(), rejected.getMessage());
                result.complete(false);
            }
        }
    }

    /**
     * Computes the exponential backoff delay for the given attempt index, capped at the
     * configured maximum backoff.
     *
     * @param attempt the zero-based index of the attempt that just failed
     * @return the delay in milliseconds before the next attempt
     */
    long nextDelay(int attempt) {
        long delay = backoffMs * (1L << Math.min(attempt, 30));
        return Math.min(delay, maxBackoffMs);
    }

    /**
     * Shuts down the internally managed scheduler, if this handler created it.
     */
    @Override
    public void close() {
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }
}
