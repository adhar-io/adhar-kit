package com.adhar.kit.dapr.pubsub;

/**
 * Outcome of dispatching a CloudEvent to a subscribed handler method, following Dapr's
 * pub/sub route-response status semantics.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum DispatchStatus {

    /** The handler completed successfully; Dapr should acknowledge the message. */
    SUCCESS,

    /** The handler failed; Dapr should retry delivery. */
    RETRY,

    /** The message should be dropped (or routed to the dead-letter topic) without retrying. */
    DROP
}
