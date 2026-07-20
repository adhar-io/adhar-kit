package com.adhar.adharkit.logging.batch;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;

import java.util.Objects;
import java.util.UUID;

/**
 * Structured lifecycle logging for batch jobs.
 *
 * <p>Emits {@link AppLogEventType#BATCH} events for job start/completion/failure, step
 * transitions, periodic progress (every {@code adhar.logging.batch.progress-log-interval}
 * processed items, with throughput) and item errors (capped at
 * {@code adhar.logging.batch.max-item-errors-logged} detailed events per run).</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * try (BatchJobRun run = batchJobLogger.startJob("nightly-reconciliation")) {
 *     run.startStep("load");
 *     for (Record r : records) {
 *         try {
 *             process(r);
 *             run.itemProcessed();
 *         } catch (Exception e) {
 *             run.itemFailed(e, r.id());
 *         }
 *     }
 *     run.complete();
 * } // close() completes (or fails) the run if not already finished
 * }</pre>
 *
 * <p>Alternatively annotate a job method with
 * {@link com.adhar.adharkit.logging.annotation.LogBatchJob @LogBatchJob}.</p>
 */
public class BatchJobLogger {

    private final AdharLoggingProperties properties;
    private final AppLogEventPublisher publisher;

    /**
     * Creates the batch job logger.
     *
     * @param properties logging properties (batch section)
     * @param publisher  event pipeline
     */
    public BatchJobLogger(AdharLoggingProperties properties, AppLogEventPublisher publisher) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    /**
     * Starts a tracked batch job run and publishes its STARTED event.
     *
     * @param jobName the job name
     * @return the run handle used to record progress and completion
     */
    public BatchJobRun startJob(String jobName) {
        BatchJobRun run = new BatchJobRun(jobName, UUID.randomUUID().toString(),
                properties.getBatch(), publisher);
        if (properties.getBatch().isEnabled()) {
            publisher.publish(AppLogEvent.builder()
                    .type(AppLogEventType.BATCH)
                    .category("batch")
                    .name(jobName)
                    .outcome(AppLogEventOutcome.STARTED)
                    .metadata("jobId", run.getJobId())
                    .build());
        }
        return run;
    }
}
