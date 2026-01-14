package com.adhar.kit.grpc.exception;

import io.grpc.Status;

/**
 * Exception for service configuration errors.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * throw new GrpcServiceConfigurationException("Failed to configure gRPC server on port 9090");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class GrpcServiceConfigurationException extends GrpcException {

    public GrpcServiceConfigurationException(String message) {
        super(Status.INTERNAL, message);
    }

    public GrpcServiceConfigurationException(String message, Throwable cause) {
        super(Status.INTERNAL, message, cause);
    }
}

