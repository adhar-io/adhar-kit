package com.adhar.kit.batch.fault;

import java.time.LocalDate;

/**
 * Where a fault-tolerant step's skipped items go ({@link FaultTolerance#deadLetter()}).
 * {@link JdbcDeadLetterSink} is the default; S3, Kafka and RabbitMQ sinks are Phase 3.
 *
 * <p>The sink is called on the step's thread, inside the chunk's transaction: a JDBC sink on the
 * JobRepository's database commits a dead letter with the chunk that skipped it, and rolls it back
 * with a chunk that fails.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@FunctionalInterface
public interface DeadLetterSink {

    /**
     * @param entry the skipped item and why
     */
    void write(Entry entry);

    /**
     * One skipped item.
     *
     * @param jobExecutionId the execution that skipped it
     * @param jobName        the job
     * @param stepName       the step
     * @param businessDate   the run's business date, {@code null} when it has none
     * @param phase          {@code read}, {@code process} or {@code write}
     * @param itemType       the item's Java type, {@code null} for a read failure (there is no item)
     * @param item           the item as JSON, {@code null} when there is no item or it could not be
     *                       serialised
     * @param errorClass     the exception's class
     * @param errorMessage   the exception's message, may be {@code null}
     */
    record Entry(long jobExecutionId, String jobName, String stepName, LocalDate businessDate, String phase,
                 String itemType, String item, String errorClass, String errorMessage) {
    }
}
