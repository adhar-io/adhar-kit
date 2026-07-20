package com.adhar.adharkit.logging.event;

import org.slf4j.event.Level;

import java.util.Map;
import java.util.Objects;

/**
 * High-level API for logging business events and tracking operations as {@link AppLogEvent}s.
 *
 * <p><b>Business events</b> record domain milestones:</p>
 * <pre>{@code
 * businessEventLogger.businessEvent("order", "ORDER_PLACED",
 *         Map.of("orderId", orderId, "amount", total));
 * }</pre>
 *
 * <p><b>Operations</b> are timed units of work. The {@link OperationScope} is try-with-resources
 * friendly and publishes a single OPERATION event with duration and outcome when closed:</p>
 * <pre>{@code
 * try (var op = businessEventLogger.startOperation("order.fulfil")) {
 *     op.metadata("orderId", orderId);
 *     fulfil(orderId);
 *     op.success();
 * } // failure() is implied when close() is reached after an exception without success()
 * }</pre>
 *
 * <p>For declarative usage annotate methods with
 * {@link com.adhar.adharkit.logging.annotation.LogOperation @LogOperation} or
 * {@link com.adhar.adharkit.logging.annotation.BusinessEvent @BusinessEvent}.</p>
 */
public class BusinessEventLogger {

    private final AppLogEventPublisher publisher;

    /**
     * Creates the logger.
     *
     * @param publisher pipeline the events are published to
     */
    public BusinessEventLogger(AppLogEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    // ==================== Business events ====================

    /**
     * Publishes a successful business event.
     *
     * @param category business domain/category (e.g. "order")
     * @param action   event name (e.g. "ORDER_PLACED")
     */
    public void businessEvent(String category, String action) {
        businessEvent(category, action, null, null);
    }

    /**
     * Publishes a successful business event with metadata.
     *
     * @param category business domain/category
     * @param action   event name
     * @param metadata contextual data (masked automatically)
     */
    public void businessEvent(String category, String action, Map<String, ?> metadata) {
        businessEvent(category, action, null, metadata);
    }

    /**
     * Publishes a successful business event with message and metadata.
     *
     * @param category business domain/category
     * @param action   event name
     * @param message  human-readable description (may be null)
     * @param metadata contextual data (may be null, masked automatically)
     */
    public void businessEvent(String category, String action, String message, Map<String, ?> metadata) {
        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.BUSINESS)
                .category(category)
                .name(action)
                .message(message)
                .outcome(AppLogEventOutcome.SUCCESS)
                .metadata(metadata)
                .build());
    }

    /**
     * Publishes a failed business event.
     *
     * @param category business domain/category
     * @param action   event name
     * @param error    the failure cause (may be null)
     * @param metadata contextual data (may be null)
     */
    public void businessEventFailed(String category, String action, Throwable error, Map<String, ?> metadata) {
        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.BUSINESS)
                .category(category)
                .name(action)
                .outcome(AppLogEventOutcome.FAILURE)
                .severity(Level.ERROR)
                .error(error)
                .metadata(metadata)
                .build());
    }

    // ==================== Operation tracking ====================

    /**
     * Publishes an operation STARTED event.
     *
     * @param operation operation name
     */
    public void operationStarted(String operation) {
        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.OPERATION)
                .name(operation)
                .outcome(AppLogEventOutcome.STARTED)
                .build());
    }

    /**
     * Publishes a successful operation event with its duration.
     *
     * @param operation  operation name
     * @param durationMs elapsed time in milliseconds
     * @param metadata   contextual data (may be null)
     */
    public void operationSucceeded(String operation, long durationMs, Map<String, ?> metadata) {
        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.OPERATION)
                .name(operation)
                .outcome(AppLogEventOutcome.SUCCESS)
                .durationMs(durationMs)
                .metadata(metadata)
                .build());
    }

    /**
     * Publishes a failed operation event with its duration and error details.
     *
     * @param operation  operation name
     * @param durationMs elapsed time in milliseconds
     * @param error      failure cause (may be null)
     * @param metadata   contextual data (may be null)
     */
    public void operationFailed(String operation, long durationMs, Throwable error, Map<String, ?> metadata) {
        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.OPERATION)
                .name(operation)
                .outcome(AppLogEventOutcome.FAILURE)
                .severity(Level.ERROR)
                .durationMs(durationMs)
                .error(error)
                .metadata(metadata)
                .build());
    }

    /**
     * Starts a timed operation scope. Close it (ideally with try-with-resources) to publish the
     * OPERATION event with duration and outcome.
     *
     * @param operation operation name
     * @return the open scope
     */
    public OperationScope startOperation(String operation) {
        return new OperationScope(operation, null);
    }

    /**
     * Starts a timed operation scope with a category.
     *
     * @param operation operation name
     * @param category  operation category (e.g. subsystem name)
     * @return the open scope
     */
    public OperationScope startOperation(String operation, String category) {
        return new OperationScope(operation, category);
    }

    /**
     * A timed, auto-closing tracker for one operation. Unless {@link #success()} or
     * {@link #failure(Throwable)} is called, closing the scope records the outcome as SUCCESS.
     */
    public final class OperationScope implements AutoCloseable {

        private final String operation;
        private final String category;
        private final long startNanos = System.nanoTime();
        private final AppLogEvent.Builder builder;
        private AppLogEventOutcome outcome = AppLogEventOutcome.SUCCESS;
        private Level severity = Level.INFO;
        private boolean closed;

        private OperationScope(String operation, String category) {
            this.operation = operation;
            this.category = category;
            this.builder = AppLogEvent.builder();
        }

        /**
         * Adds contextual metadata to the operation event.
         *
         * @param key   metadata key
         * @param value metadata value
         * @return this scope
         */
        public OperationScope metadata(String key, Object value) {
            builder.metadata(key, value);
            return this;
        }

        /**
         * Marks the operation as successful (the default).
         *
         * @return this scope
         */
        public OperationScope success() {
            this.outcome = AppLogEventOutcome.SUCCESS;
            this.severity = Level.INFO;
            return this;
        }

        /**
         * Marks the operation with an explicit outcome.
         *
         * @param outcome the outcome to record
         * @return this scope
         */
        public OperationScope outcome(AppLogEventOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        /**
         * Marks the operation as failed and records the error.
         *
         * @param error failure cause (may be null)
         * @return this scope
         */
        public OperationScope failure(Throwable error) {
            this.outcome = AppLogEventOutcome.FAILURE;
            this.severity = Level.ERROR;
            builder.error(error);
            return this;
        }

        /**
         * Publishes the operation event (idempotent).
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            publisher.publish(builder
                    .type(AppLogEventType.OPERATION)
                    .name(operation)
                    .category(category)
                    .outcome(outcome)
                    .severity(severity)
                    .durationMs(durationMs)
                    .build());
        }
    }
}
