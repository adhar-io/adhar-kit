package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.config.BatchProperties;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The fault tolerance of a chunk-oriented step: what to retry, what to skip, how often.
 *
 * <pre>{@code
 * steps.<In, Out>chunk("importStep")
 *     .reader(reader).processor(processor).writer(writer)
 *     .faultTolerance(FaultTolerance.transientInfrastructure()   // retry presets
 *         .skip(ValidationException.class, 100))                 // SkipPolicy + limit
 *     .build();
 * }</pre>
 *
 * <p>Immutable: every method returns a new value. Retry delays and attempts default to
 * {@code adhar.batch.retry.*} and are resolved when the step is built. Retrying happens per item,
 * inside the chunk's transaction; a statement that failed aborts a PostgreSQL transaction, so a
 * retried database <em>write</em> in the same chunk cannot succeed and the run resumes from its
 * last commit on restart instead. Retry earns its keep on reads, on processors that call other
 * systems and on connection acquisition.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class FaultTolerance {

    private static final FaultTolerance NONE = new FaultTolerance(null, null, null, null, null,
            Set.of(), 0, null);

    private final Predicate<Throwable> retryable;
    private final Integer maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final RetryPolicy retryPolicy;
    private final Set<Class<? extends Throwable>> skippable;
    private final long skipLimit;
    private final SkipPolicy skipPolicy;

    private FaultTolerance(Predicate<Throwable> retryable, Integer maxAttempts, Duration initialBackoff,
                           Duration maxBackoff, RetryPolicy retryPolicy, Set<Class<? extends Throwable>> skippable,
                           long skipLimit, SkipPolicy skipPolicy) {
        this.retryable = retryable;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.retryPolicy = retryPolicy;
        this.skippable = Collections.unmodifiableSet(new LinkedHashSet<>(skippable));
        this.skipLimit = skipLimit;
        this.skipPolicy = skipPolicy;
    }

    /**
     * @return no retry and no skip: any item failure fails the chunk (the default)
     */
    public static FaultTolerance none() {
        return NONE;
    }

    /**
     * @return retry {@linkplain RetryPresets#TRANSIENT_INFRASTRUCTURE transient database and
     *         network failures}
     */
    public static FaultTolerance transientInfrastructure() {
        return NONE.withRetry(RetryPresets.TRANSIENT_INFRASTRUCTURE);
    }

    /**
     * @return retry {@linkplain RetryPresets#TRANSIENT_HTTP transient HTTP failures}
     */
    public static FaultTolerance transientHttp() {
        return NONE.withRetry(RetryPresets.TRANSIENT_HTTP);
    }

    /**
     * @param types the failures to retry (and their subclasses, anywhere in the cause chain)
     * @return retry those failures with the configured back-off
     */
    @SafeVarargs
    public static FaultTolerance retryOn(Class<? extends Throwable>... types) {
        return NONE.withRetry(RetryPresets.typesPredicate(Arrays.asList(types)));
    }

    /**
     * Also retries the given failures.
     *
     * @param types more failures to retry
     * @return the extended fault tolerance
     */
    @SafeVarargs
    public final FaultTolerance alsoRetryOn(Class<? extends Throwable>... types) {
        Predicate<Throwable> more = RetryPresets.typesPredicate(Arrays.asList(types));
        return withRetry(retryable == null ? more : retryable.or(more));
    }

    /**
     * @param attempts attempts per item, the first one included
     * @return the same, with that many attempts instead of {@code adhar.batch.retry.max-attempts}
     */
    public FaultTolerance maxAttempts(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1, was " + attempts);
        }
        return new FaultTolerance(retryable, attempts, initialBackoff, maxBackoff, retryPolicy, skippable,
                skipLimit, skipPolicy);
    }

    /**
     * @param initial delay before the first retry
     * @param max     upper bound of a delay
     * @return the same, with this back-off instead of {@code adhar.batch.retry.*}
     */
    public FaultTolerance backoff(Duration initial, Duration max) {
        if (initial == null || max == null || initial.isNegative() || initial.isZero() || max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("backoff needs a positive initial delay and a max not below it");
        }
        return new FaultTolerance(retryable, maxAttempts, initial, max, retryPolicy, skippable, skipLimit, skipPolicy);
    }

    /**
     * @param policy a retry policy of the application's own, replacing the presets
     * @return the same, retrying by that policy
     */
    public FaultTolerance retryPolicy(RetryPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        return new FaultTolerance(retryable, maxAttempts, initialBackoff, maxBackoff, policy, skippable, skipLimit,
                skipPolicy);
    }

    /**
     * Lets the step skip items failing with {@code type}, up to {@code limit} skips for the whole
     * step. One limit applies to every skippable type of a step: declaring a different one fails.
     *
     * @param type  a failure to skip (and its subclasses)
     * @param limit the most items the step may skip in total
     * @return the extended fault tolerance
     */
    public FaultTolerance skip(Class<? extends Throwable> type, long limit) {
        return skip(limit, type);
    }

    /**
     * Lets the step skip items failing with any of {@code types}, up to {@code limit} skips for
     * the whole step.
     *
     * @param limit the most items the step may skip in total
     * @param types failures to skip (and their subclasses)
     * @return the extended fault tolerance
     */
    @SafeVarargs
    public final FaultTolerance skip(long limit, Class<? extends Throwable>... types) {
        if (limit < 1) {
            throw new IllegalArgumentException("a skip limit must be at least 1, was " + limit);
        }
        if (types.length == 0) {
            throw new IllegalArgumentException("at least one skippable exception type is required");
        }
        if (skipLimit != 0 && skipLimit != limit) {
            throw new IllegalStateException("A step has one skip limit for all its skippable failures; it is "
                    + skipLimit + " already, not " + limit);
        }
        Set<Class<? extends Throwable>> more = new LinkedHashSet<>(skippable);
        more.addAll(Arrays.asList(types));
        return new FaultTolerance(retryable, maxAttempts, initialBackoff, maxBackoff, retryPolicy, more, limit,
                skipPolicy);
    }

    /**
     * @param policy a skip policy of the application's own, replacing {@link #skip(long, Class[])}
     * @return the same, skipping by that policy
     */
    public FaultTolerance skipPolicy(SkipPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        return new FaultTolerance(retryable, maxAttempts, initialBackoff, maxBackoff, retryPolicy, skippable,
                skipLimit, policy);
    }

    /**
     * @return whether anything is retried or skipped
     */
    public boolean isFaultTolerant() {
        return retryable != null || retryPolicy != null || !skippable.isEmpty() || skipPolicy != null;
    }

    /**
     * Resolves the retry policy against the configured defaults.
     *
     * @param defaults {@code adhar.batch.retry.*}
     * @return the policy, or {@code null} when nothing is retried
     */
    public RetryPolicy resolveRetryPolicy(BatchProperties.Retry defaults) {
        if (retryPolicy != null) {
            return retryPolicy;
        }
        if (retryable == null) {
            return null;
        }
        return RetryPresets.policy(retryable,
                maxAttempts != null ? maxAttempts : defaults.getMaxAttempts(),
                initialBackoff != null ? initialBackoff : defaults.getInitialBackoff(),
                maxBackoff != null ? maxBackoff : defaults.getMaxBackoff());
    }

    /**
     * @return the skip policy: the application's own, a limit over the skippable types, or never
     */
    public SkipPolicy resolveSkipPolicy() {
        if (skipPolicy != null) {
            return skipPolicy;
        }
        return skippable.isEmpty() ? SkipPresets.never() : SkipPresets.limit(skipLimit, skippable);
    }

    /**
     * Applies this fault tolerance to a Spring Batch 6 chunk-oriented step builder.
     *
     * @param builder  the builder
     * @param defaults {@code adhar.batch.retry.*}
     * @param <I>      input type
     * @param <O>      output type
     */
    public <I, O> void applyTo(ChunkOrientedStepBuilder<I, O> builder, BatchProperties.Retry defaults) {
        if (!isFaultTolerant()) {
            return;
        }
        builder.faultTolerant();
        RetryPolicy retry = resolveRetryPolicy(defaults);
        if (retry != null) {
            builder.retryPolicy(retry);
        }
        builder.skipPolicy(resolveSkipPolicy());
    }

    /**
     * @return the skippable failure types
     */
    public Set<Class<? extends Throwable>> getSkippable() {
        return skippable;
    }

    /**
     * @return the step's skip limit, 0 when nothing is skippable by type
     */
    public long getSkipLimit() {
        return skipLimit;
    }

    private FaultTolerance withRetry(Predicate<Throwable> predicate) {
        return new FaultTolerance(predicate, maxAttempts, initialBackoff, maxBackoff, retryPolicy, skippable,
                skipLimit, skipPolicy);
    }
}
