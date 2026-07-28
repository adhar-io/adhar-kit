package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.metrics.BatchMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;

/**
 * A Spring Batch {@link SkipListener} that logs skipped items and records each
 * skip to an optional {@link BatchMetrics}.
 *
 * <p>Skips are recorded against the metric step named {@code "skips"} (a single
 * write to the {@code adhar.batch.step.skips} counter per skipped item), since a
 * {@code SkipListener} does not expose the owning step name. The metrics
 * collector is optional; when {@code null} the recording is silently skipped.</p>
 *
 * @param <T> the item type read
 * @param <S> the item type written
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharSkipListener<T, S> implements SkipListener<T, S> {

    /** Synthetic step name used when recording skips, as the real step is unknown here. */
    static final String SKIP_METRIC_STEP = "skips";

    private final BatchMetrics batchMetrics;

    /**
     * Creates a listener that only logs skips (no metrics).
     */
    public AdharSkipListener() {
        this(null);
    }

    /**
     * Creates a listener that records skip metrics.
     *
     * @param batchMetrics the metrics collector, may be {@code null}
     */
    public AdharSkipListener(BatchMetrics batchMetrics) {
        this.batchMetrics = batchMetrics;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped item during read: {}", t.getMessage());
        recordSkip();
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        log.warn("Skipped item during write [{}]: {}", item, t.getMessage());
        recordSkip();
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        log.warn("Skipped item during process [{}]: {}", item, t.getMessage());
        recordSkip();
    }

    private void recordSkip() {
        if (batchMetrics == null) {
            return;
        }
        try {
            batchMetrics.recordStepExecution(SKIP_METRIC_STEP, 0, 0, 1);
        } catch (RuntimeException ex) {
            log.warn("Failed to record skip metric: {}", ex.getMessage());
        }
    }
}
