package com.adhar.kit.batch.retry;

import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A fluent helper for configuring retry and skip policies on a Spring Batch step.
 *
 * <p>Wraps a {@link SimpleStepBuilder} and applies retry/skip configuration,
 * returning a {@link FaultTolerantStepBuilder} for further customization.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var step = new RetryableStepBuilder<>(simpleStepBuilder)
 *         .withRetryLimit(3)
 *         .retryOn(TransientDataAccessException.class)
 *         .withSkipLimit(10)
 *         .skipOn(FlatFileParseException.class)
 *         .build();
 * }</pre>
 *
 * @param <I> the input item type
 * @param <O> the output item type
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class RetryableStepBuilder<I, O> {

    private final SimpleStepBuilder<I, O> stepBuilder;
    private int retryLimit = 3;
    private int skipLimit = 0;
    private final List<Class<? extends Throwable>> retryableExceptions = new ArrayList<>();
    private final List<Class<? extends Throwable>> skippableExceptions = new ArrayList<>();

    /**
     * Creates a new RetryableStepBuilder wrapping the given step builder.
     *
     * @param stepBuilder the simple step builder to configure
     */
    public RetryableStepBuilder(SimpleStepBuilder<I, O> stepBuilder) {
        this.stepBuilder = stepBuilder;
    }

    /**
     * Sets the maximum number of retry attempts for retryable exceptions.
     *
     * @param limit the retry limit (must be positive)
     * @return this builder for chaining
     */
    public RetryableStepBuilder<I, O> withRetryLimit(int limit) {
        this.retryLimit = limit;
        return this;
    }

    /**
     * Adds exception types that should trigger a retry.
     *
     * @param exceptions the exception classes to retry on
     * @return this builder for chaining
     */
    @SafeVarargs
    public final RetryableStepBuilder<I, O> retryOn(Class<? extends Exception>... exceptions) {
        for (var exception : exceptions) {
            retryableExceptions.add(exception);
        }
        return this;
    }

    /**
     * Sets the maximum number of items that can be skipped before the step fails.
     *
     * @param limit the skip limit (must be non-negative)
     * @return this builder for chaining
     */
    public RetryableStepBuilder<I, O> withSkipLimit(int limit) {
        this.skipLimit = limit;
        return this;
    }

    /**
     * Adds exception types that should be skipped rather than causing step failure.
     *
     * @param exceptions the exception classes to skip on
     * @return this builder for chaining
     */
    @SafeVarargs
    public final RetryableStepBuilder<I, O> skipOn(Class<? extends Exception>... exceptions) {
        for (var exception : exceptions) {
            skippableExceptions.add(exception);
        }
        return this;
    }

    /**
     * Applies the configured retry and skip policies to the step builder
     * and returns a {@link FaultTolerantStepBuilder} for further configuration.
     *
     * @return the fault-tolerant step builder with retry and skip policies applied
     */
    @SuppressWarnings("unchecked")
    public FaultTolerantStepBuilder<I, O> build() {
        var faultTolerant = stepBuilder.faultTolerant();

        faultTolerant.retryLimit(retryLimit);
        for (var ex : retryableExceptions) {
            faultTolerant.retry((Class<? extends Throwable>) ex);
        }

        if (skipLimit > 0 || !skippableExceptions.isEmpty()) {
            faultTolerant.skipLimit(skipLimit);
            for (var ex : skippableExceptions) {
                faultTolerant.skip((Class<? extends Throwable>) ex);
            }
        }

        return faultTolerant;
    }
}
