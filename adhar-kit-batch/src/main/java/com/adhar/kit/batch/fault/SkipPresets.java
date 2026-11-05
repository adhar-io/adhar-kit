package com.adhar.kit.batch.fault;

import org.springframework.batch.core.step.skip.LimitCheckingExceptionHierarchySkipPolicy;
import org.springframework.batch.core.step.skip.NeverSkipItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link SkipPolicy} presets for chunk-oriented steps.
 *
 * <p>A skip limit counts every skip of the step together (Spring Batch's model, restored on a
 * restart). Exceeding it fails the step with {@code SkipLimitExceededException}: a
 * non-retryable failure (exit code 3 under the Kubernetes runner), because the data, not the
 * infrastructure, is at fault.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class SkipPresets {

    private SkipPresets() {
    }

    /**
     * @return a policy that never skips: any item failure fails the chunk
     */
    public static SkipPolicy never() {
        return new NeverSkipItemSkipPolicy();
    }

    /**
     * @param limit the most items the step may skip in total
     * @param types the failures that may be skipped (and their subclasses)
     * @return a limit-checking policy over those types
     */
    public static SkipPolicy limit(long limit, Collection<Class<? extends Throwable>> types) {
        if (limit < 1) {
            throw new IllegalArgumentException("a skip limit must be at least 1, was " + limit);
        }
        Set<Class<? extends Throwable>> skippable = new LinkedHashSet<>(types);
        if (skippable.isEmpty()) {
            throw new IllegalArgumentException("at least one skippable exception type is required");
        }
        return new LimitCheckingExceptionHierarchySkipPolicy(skippable, limit);
    }
}
