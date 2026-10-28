package com.adhar.kit.dapr.pubsub;

/**
 * Thrown by a subscriber to tell Dapr to drop the message instead of redelivering it: the event
 * can never succeed (malformed, for an entity that no longer exists, ...). Every other exception
 * asks for a retry.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DropEventException extends RuntimeException {

    public DropEventException(String message) {
        super(message);
    }

    public DropEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
