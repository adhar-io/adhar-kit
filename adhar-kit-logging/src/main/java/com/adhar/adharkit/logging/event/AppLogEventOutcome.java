package com.adhar.adharkit.logging.event;

/**
 * Outcome/status of the operation an {@link AppLogEvent} describes.
 */
public enum AppLogEventOutcome {

    /** The operation has started. */
    STARTED,

    /** The operation is still running (progress events). */
    IN_PROGRESS,

    /** The operation completed successfully. */
    SUCCESS,

    /** The operation failed. */
    FAILURE,

    /** The operation completed with partial success (e.g. batch with skipped items). */
    PARTIAL,

    /** The operation was denied (authorization/validation rejection). */
    DENIED,

    /** The operation timed out. */
    TIMEOUT,

    /** The operation was cancelled. */
    CANCELLED,

    /** The outcome is not known. */
    UNKNOWN
}
