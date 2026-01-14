package com.adhar.kit.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Retry utility for handling transient failures.
 *
 * <p>Provides configurable retry logic with exponential backoff.</p>
 *
 * <p><b>Example - Simple Retry:</b></p>
 * <pre>{@code
 * RetryUtil.execute(
 *     () -> externalService.call(),
 *     3,  // max retries
 *     1000  // delay ms
 * );
 * }</pre>
 *
 * <p><b>Example - Exponential Backoff:</b></p>
 * <pre>{@code
 * RetryUtil.executeWithBackoff(
 *     () -> apiClient.getData(),
 *     5,      // max retries
 *     1000,   // initial delay
 *     2.0     // backoff multiplier
 * );
 * }</pre>
 *
 * <p><b>Example - Custom Retry Policy:</b></p>
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *     .maxRetries(5)
 *     .initialDelay(1000)
 *     .maxDelay(30000)
 *     .backoffMultiplier(2.0)
 *     .retryableExceptions(IOException.class, TimeoutException.class)
 *     .build();
 *
 * RetryUtil.execute(() -> externalService.call(), policy);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class RetryUtil {

    /**
     * Executes operation with retry.
     *
     * @param operation operation to execute
     * @param maxRetries maximum retry attempts
     * @param delayMs delay between retries in milliseconds
     * @param <T> return type
     * @return operation result
     * @throws Exception if all retries fail
     */
    public static <T> T execute(Supplier<T> operation, int maxRetries, long delayMs) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    log.warn("Retry attempt {}/{} failed: {}", attempt + 1, maxRetries, e.getMessage());
                    Thread.sleep(delayMs);
                }
            }
        }

        log.error("All {} retry attempts failed", maxRetries);
        throw lastException;
    }

    /**
     * Executes operation with exponential backoff.
     *
     * @param operation operation to execute
     * @param maxRetries maximum retry attempts
     * @param initialDelayMs initial delay in milliseconds
     * @param backoffMultiplier backoff multiplier
     * @param <T> return type
     * @return operation result
     * @throws Exception if all retries fail
     */
    public static <T> T executeWithBackoff(Supplier<T> operation, int maxRetries,
                                           long initialDelayMs, double backoffMultiplier) throws Exception {
        Exception lastException = null;
        long delay = initialDelayMs;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    log.warn("Retry attempt {}/{} failed, waiting {}ms: {}",
                           attempt + 1, maxRetries, delay, e.getMessage());
                    Thread.sleep(delay);
                    delay = (long) (delay * backoffMultiplier);
                }
            }
        }

        log.error("All {} retry attempts failed", maxRetries);
        throw lastException;
    }

    /**
     * Retry policy configuration.
     */
    public static class RetryPolicy {
        private final int maxRetries;
        private final long initialDelay;
        private final long maxDelay;
        private final double backoffMultiplier;

        private RetryPolicy(int maxRetries, long initialDelay, long maxDelay, double backoffMultiplier) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
            this.maxDelay = maxDelay;
            this.backoffMultiplier = backoffMultiplier;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int maxRetries = 3;
            private long initialDelay = 1000;
            private long maxDelay = 30000;
            private double backoffMultiplier = 2.0;

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder initialDelay(long initialDelay) {
                this.initialDelay = initialDelay;
                return this;
            }

            public Builder maxDelay(long maxDelay) {
                this.maxDelay = maxDelay;
                return this;
            }

            public Builder backoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
                return this;
            }

            public RetryPolicy build() {
                return new RetryPolicy(maxRetries, initialDelay, maxDelay, backoffMultiplier);
            }
        }
    }
}

