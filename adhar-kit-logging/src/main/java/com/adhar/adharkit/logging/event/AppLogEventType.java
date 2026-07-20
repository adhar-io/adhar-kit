package com.adhar.adharkit.logging.event;

/**
 * Classification of an {@link AppLogEvent}.
 */
public enum AppLogEventType {

    /** A domain/business milestone (order placed, payment captured, user registered). */
    BUSINESS,

    /** A tracked technical operation inside the application (service call, workflow step). */
    OPERATION,

    /** An inbound/outbound REST API exchange. */
    API,

    /** A batch job lifecycle or progress event. */
    BATCH,

    /** A performance measurement (slow operation, timing summary). */
    PERFORMANCE,

    /** A security/compliance audit trail entry. */
    AUDIT,

    /** A security-relevant event (login failure, access denied). */
    SECURITY,

    /** An application/system lifecycle event (startup, shutdown, config change). */
    SYSTEM
}
