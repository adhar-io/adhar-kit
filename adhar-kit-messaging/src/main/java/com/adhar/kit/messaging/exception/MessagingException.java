package com.adhar.kit.messaging.exception;

/**
 * Runtime exception thrown by the messaging module when an operation cannot be
 * completed - a request-reply call that times out or has no broker wired, an
 * outbox publish attempted without an outbox store, or a payload that cannot be
 * (de)serialized.
 */
public class MessagingException extends RuntimeException {

    /**
     * Creates a new messaging exception with the given message.
     *
     * @param message description of the failure
     */
    public MessagingException(String message) {
        super(message);
    }

    /**
     * Creates a new messaging exception with the given message and cause.
     *
     * @param message description of the failure
     * @param cause   the underlying cause
     */
    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
