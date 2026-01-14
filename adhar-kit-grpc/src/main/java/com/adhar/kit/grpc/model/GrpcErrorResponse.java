package com.adhar.kit.grpc.model;

import io.grpc.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard gRPC error response model.
 *
 * <p>Provides consistent error structure for gRPC services.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * GrpcErrorResponse error = GrpcErrorResponse.builder()
 *     .status(Status.Code.INVALID_ARGUMENT)
 *     .message("Invalid order ID")
 *     .correlationId(correlationId)
 *     .detail("orderId", "Order ID must be non-empty")
 *     .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrpcErrorResponse {

    /**
     * gRPC status code.
     */
    private Status.Code status;

    /**
     * Error message.
     */
    private String message;

    /**
     * Error description.
     */
    private String description;

    /**
     * Service name.
     */
    private String service;

    /**
     * Method name.
     */
    private String method;

    /**
     * Correlation ID for tracing.
     */
    private String correlationId;

    /**
     * Request ID.
     */
    private String requestId;

    /**
     * Timestamp.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Additional error details.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Adds detail to error.
     *
     * @param key detail key
     * @param value detail value
     * @return this error for chaining
     */
    public GrpcErrorResponse detail(String key, Object value) {
        details.put(key, value);
        return this;
    }
}

