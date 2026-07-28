package com.adhar.kit.batch.retry;

import org.springframework.batch.core.step.builder.SimpleStepBuilder;

/**
 * Factory that produces {@link RetryableStepBuilder} instances pre-seeded with
 * the module's default retry configuration.
 *
 * <p>The defaults are sourced from {@code adhar.batch.max-retries} and
 * {@code adhar.batch.retry-on-failure} and wired in by the batch
 * auto-configuration. Injecting this factory (instead of constructing
 * {@link RetryableStepBuilder} directly) means step definitions automatically
 * pick up the configured retry policy, while explicit
 * {@link RetryableStepBuilder#withRetryLimit(int)} calls still override it.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var step = retryableStepBuilderFactory.create(simpleStepBuilder)
 *         .retryOn(TransientDataAccessException.class)
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class RetryableStepBuilderFactory {

    private final int defaultRetryLimit;
    private final boolean retryEnabled;

    /**
     * Creates a factory with the given retry defaults.
     *
     * @param defaultRetryLimit the default retry limit applied when retry is enabled
     * @param retryEnabled      whether automatic retry is applied by default
     */
    public RetryableStepBuilderFactory(int defaultRetryLimit, boolean retryEnabled) {
        this.defaultRetryLimit = defaultRetryLimit;
        this.retryEnabled = retryEnabled;
    }

    /**
     * Creates a {@link RetryableStepBuilder} seeded with the configured defaults.
     *
     * @param stepBuilder the simple step builder to wrap
     * @param <I>         the input item type
     * @param <O>         the output item type
     * @return a pre-configured retryable step builder
     */
    public <I, O> RetryableStepBuilder<I, O> create(SimpleStepBuilder<I, O> stepBuilder) {
        return new RetryableStepBuilder<>(stepBuilder, defaultRetryLimit, retryEnabled);
    }

    /**
     * @return the default retry limit applied when retry is enabled
     */
    public int getDefaultRetryLimit() {
        return defaultRetryLimit;
    }

    /**
     * @return whether automatic retry is applied by default
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }
}
