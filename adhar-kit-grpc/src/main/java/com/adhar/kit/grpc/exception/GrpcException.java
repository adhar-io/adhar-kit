package com.adhar.kit.grpc.exception;

import io.grpc.Status;

/**
 * Base exception for gRPC operations.
 *
 * <p>Provides automatic conversion to gRPC Status codes.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * throw new GrpcException(Status.INVALID_ARGUMENT, "Invalid order ID");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class GrpcException extends RuntimeException {

    private final Status status;

    /**
     * Creates exception with status and message.
     *
     * @param status gRPC status
     * @param message error message
     */
    public GrpcException(Status status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Creates exception with status, message, and cause.
     *
     * @param status gRPC status
     * @param message error message
     * @param cause underlying cause
     */
    public GrpcException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Gets gRPC status.
     *
     * @return gRPC status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Converts to gRPC StatusRuntimeException.
     *
     * @return StatusRuntimeException
     */
    public io.grpc.StatusRuntimeException toStatusRuntimeException() {
        return status.withDescription(getMessage()).withCause(this).asRuntimeException();
    }
}

