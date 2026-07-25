package com.adhar.kit.messaging.core;

import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties.CommonProperties.RetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * {@link MessageHandler} decorator that retries a delegate handler with exponential
 * backoff, driven by {@link RetryProperties} (bound from
 * {@code adhar.messaging.common.retry.*}).
 * <p>
 * The delegate is retried when it either throws an exception or returns {@code false}.
 * Once the maximum number of attempts is reached without success, the failure is
 * reported to the optional {@link DeadLetterPublisher} (if configured) and this handler
 * returns {@code false}.
 * <p>
 * The sleep between attempts is performed through an injectable {@link Sleeper} rather
 * than a hard-coded {@code Thread.sleep}, so unit tests can exercise the full retry loop
 * (including backoff growth) without actually waiting.
 *
 * @param <T> the type of the message payload
 */
public class RetryingMessageHandler<T> implements MessageHandler<T> {

    private static final Logger log = LoggerFactory.getLogger(RetryingMessageHandler.class);

    /**
     * Seam for the delay between retry attempts, so tests can supply a no-op (or
     * recording) implementation instead of blocking for real.
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final MessageHandler<T> delegate;
    private final int maxAttempts;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    private final Sleeper sleeper;
    private final DeadLetterPublisher deadLetterPublisher;
    private final MessagingMetrics metrics;

    public RetryingMessageHandler(MessageHandler<T> delegate, RetryProperties retryProperties) {
        this(delegate, retryProperties, Thread::sleep, null, null);
    }

    public RetryingMessageHandler(MessageHandler<T> delegate, RetryProperties retryProperties, Sleeper sleeper) {
        this(delegate, retryProperties, sleeper, null, null);
    }

    public RetryingMessageHandler(MessageHandler<T> delegate, RetryProperties retryProperties, Sleeper sleeper,
                                   DeadLetterPublisher deadLetterPublisher, MessagingMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(retryProperties, "retryProperties");
        this.maxAttempts = Math.max(1, retryProperties.getMaxAttempts());
        this.initialDelayMs = Math.max(0, retryProperties.getInitialDelayMs());
        this.backoffMultiplier = retryProperties.getBackoffMultiplier() > 0
                ? retryProperties.getBackoffMultiplier() : 1.0;
        this.maxDelayMs = Math.max(this.initialDelayMs, retryProperties.getMaxDelayMs());
        this.sleeper = sleeper != null ? sleeper : Thread::sleep;
        this.deadLetterPublisher = deadLetterPublisher;
        this.metrics = metrics;
    }

    @Override
    public boolean handle(T payload, Map<String, Object> headers, MessageContext context) {
        long delay = initialDelayMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean success;
            try {
                success = delegate.handle(payload, headers, context);
                lastException = success ? null : lastException;
            } catch (Exception e) {
                success = false;
                lastException = e;
                log.warn("Attempt {}/{} failed for destination {}: {}",
                        attempt, maxAttempts, describe(context), e.getMessage());
            }

            if (success) {
                return true;
            }

            boolean lastAttempt = attempt == maxAttempts;
            if (lastAttempt) {
                break;
            }

            if (metrics != null) {
                metrics.recordRetry(describe(context));
            }
            try {
                sleeper.sleep(Math.min(delay, maxDelayMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Retry sleep interrupted for destination {} - aborting remaining attempts",
                        describe(context));
                break;
            }
            delay = (long) (delay * backoffMultiplier);
        }

        log.error("Giving up on message for destination {} after {} attempt(s)",
                describe(context), maxAttempts, lastException);
        if (metrics != null) {
            metrics.recordConsumeFailure(describe(context));
        }
        if (deadLetterPublisher != null) {
            deadLetterPublisher.publish(context, payload, headers, lastException, maxAttempts);
        }
        return false;
    }

    private static String describe(MessageContext context) {
        return context != null ? context.getDestination() : "unknown";
    }
}
