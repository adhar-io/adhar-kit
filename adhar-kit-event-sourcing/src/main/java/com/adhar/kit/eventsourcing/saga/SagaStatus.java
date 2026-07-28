package com.adhar.kit.eventsourcing.saga;

/**
 * Lifecycle status of a {@link SagaInstance}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum SagaStatus {

    /** The saga is executing forward steps or awaiting a progression event. */
    RUNNING,

    /** All steps completed successfully. */
    COMPLETED,

    /** A step failed and compensations are being executed in reverse order. */
    COMPENSATING,

    /** Compensation of previously completed steps has finished. */
    COMPENSATED,

    /** The saga terminated abnormally (e.g. a compensation itself failed). */
    FAILED
}
