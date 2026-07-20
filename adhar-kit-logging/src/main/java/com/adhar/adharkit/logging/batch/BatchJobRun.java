package com.adhar.adharkit.logging.batch;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.slf4j.event.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handle for one tracked batch job execution, created by {@link BatchJobLogger#startJob(String)}.
 *
 * <p>Thread-safe: item counters may be updated from parallel worker threads. Closing the run is
 * idempotent; if neither {@link #complete()} nor {@link #fail(Throwable)} was called,
 * {@link #close()} completes the run (as PARTIAL when item failures were recorded).</p>
 */
public class BatchJobRun implements AutoCloseable {

    private final String jobName;
    private final String jobId;
    private final AdharLoggingProperties.BatchLoggingProperties batchProperties;
    private final AppLogEventPublisher publisher;

    private final long startNanos = System.nanoTime();
    private final AtomicLong itemsProcessed = new AtomicLong();
    private final AtomicLong itemsSkipped = new AtomicLong();
    private final AtomicLong itemsFailed = new AtomicLong();
    private final AtomicInteger itemErrorsLogged = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private volatile String currentStep;
    private volatile long stepStartNanos;

    BatchJobRun(String jobName, String jobId,
                AdharLoggingProperties.BatchLoggingProperties batchProperties,
                AppLogEventPublisher publisher) {
        this.jobName = jobName;
        this.jobId = jobId;
        this.batchProperties = batchProperties;
        this.publisher = publisher;
    }

    public String getJobName() {
        return jobName;
    }

    public String getJobId() {
        return jobId;
    }

    public long getItemsProcessed() {
        return itemsProcessed.get();
    }

    public long getItemsSkipped() {
        return itemsSkipped.get();
    }

    public long getItemsFailed() {
        return itemsFailed.get();
    }

    /**
     * Marks the beginning of a named step, closing the previous step if one is open.
     *
     * @param stepName the step name
     */
    public void startStep(String stepName) {
        completeStep();
        this.currentStep = stepName;
        this.stepStartNanos = System.nanoTime();
        publish(builder("STEP_STARTED", AppLogEventOutcome.STARTED, Level.INFO)
                .metadata("step", stepName));
    }

    /**
     * Completes the currently open step (no-op when none is open).
     */
    public void completeStep() {
        String step = this.currentStep;
        if (step == null) {
            return;
        }
        this.currentStep = null;
        long durationMs = (System.nanoTime() - stepStartNanos) / 1_000_000;
        publish(builder("STEP_COMPLETED", AppLogEventOutcome.SUCCESS, Level.INFO)
                .durationMs(durationMs)
                .metadata("step", step));
    }

    /**
     * Records one successfully processed item; emits a progress event every
     * {@code progress-log-interval} items.
     */
    public void itemProcessed() {
        long processed = itemsProcessed.incrementAndGet();
        long interval = batchProperties.getProgressLogInterval();
        if (interval > 0 && processed % interval == 0) {
            long elapsedMs = elapsedMs();
            AppLogEvent.Builder builder = builder("PROGRESS", AppLogEventOutcome.IN_PROGRESS, Level.INFO)
                    .metadata("itemsProcessed", processed)
                    .metadata("itemsSkipped", itemsSkipped.get())
                    .metadata("itemsFailed", itemsFailed.get())
                    .metadata("itemsPerSecond", ratePerSecond(processed, elapsedMs));
            if (currentStep != null) {
                builder.metadata("step", currentStep);
            }
            publish(builder.durationMs(elapsedMs));
        }
    }

    /**
     * Records one skipped item.
     */
    public void itemSkipped() {
        itemsSkipped.incrementAndGet();
    }

    /**
     * Records one failed item. The first {@code max-item-errors-logged} failures are published as
     * detailed WARN events; further failures are only counted (and reported in the summary).
     *
     * @param error  the item failure (may be null)
     * @param itemId identifier of the failed item (may be null)
     */
    public void itemFailed(Throwable error, String itemId) {
        long failed = itemsFailed.incrementAndGet();
        if (itemErrorsLogged.getAndIncrement() < batchProperties.getMaxItemErrorsLogged()) {
            AppLogEvent.Builder builder = builder("ITEM_FAILED", AppLogEventOutcome.FAILURE, Level.WARN)
                    .error(error)
                    .metadata("failedCount", failed);
            if (itemId != null) {
                builder.metadata("itemId", itemId);
            }
            if (currentStep != null) {
                builder.metadata("step", currentStep);
            }
            publish(builder);
        }
    }

    /**
     * Completes the run successfully (PARTIAL when item failures were recorded) and publishes the
     * summary event. Idempotent.
     */
    public void complete() {
        finish(itemsFailed.get() > 0 ? AppLogEventOutcome.PARTIAL : AppLogEventOutcome.SUCCESS,
                itemsFailed.get() > 0 ? Level.WARN : Level.INFO, null);
    }

    /**
     * Fails the run and publishes the summary event with error details. Idempotent.
     *
     * @param error the job failure cause (may be null)
     */
    public void fail(Throwable error) {
        finish(AppLogEventOutcome.FAILURE, Level.ERROR, error);
    }

    /**
     * Completes the run if it was not explicitly completed or failed.
     */
    @Override
    public void close() {
        complete();
    }

    private void finish(AppLogEventOutcome outcome, Level severity, Throwable error) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        completeStep();
        long elapsedMs = elapsedMs();
        long processed = itemsProcessed.get();
        publish(builder("COMPLETED", outcome, severity)
                .durationMs(elapsedMs)
                .error(error)
                .metadata(summary(processed, elapsedMs)));
    }

    private Map<String, Object> summary(long processed, long elapsedMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("itemsProcessed", processed);
        summary.put("itemsSkipped", itemsSkipped.get());
        summary.put("itemsFailed", itemsFailed.get());
        summary.put("itemsPerSecond", ratePerSecond(processed, elapsedMs));
        return summary;
    }

    private AppLogEvent.Builder builder(String phase, AppLogEventOutcome outcome, Level severity) {
        return AppLogEvent.builder()
                .type(AppLogEventType.BATCH)
                .category("batch")
                .name(jobName)
                .message(phase)
                .outcome(outcome)
                .severity(severity)
                .metadata("jobId", jobId);
    }

    private void publish(AppLogEvent.Builder builder) {
        if (batchProperties.isEnabled()) {
            publisher.publish(builder.build());
        }
    }

    private long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double ratePerSecond(long items, long elapsedMs) {
        if (elapsedMs <= 0) {
            return items;
        }
        return Math.round(items * 100000.0 / elapsedMs) / 100.0;
    }
}
