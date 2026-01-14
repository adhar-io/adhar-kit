package com.adhar.kit.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standard error response model for API documentation.
 *
 * <p>Provides consistent error response structure across all APIs.</p>
 *
 * <p><b>Example JSON:</b></p>
 * <pre>{@code
 * {
 *   "timestamp": "2025-11-02T10:30:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "path": "/api/orders",
 *   "correlationId": "550e8400-e29b-41d4-a716-446655440000",
 *   "errors": [
 *     {
 *       "field": "customerId",
 *       "message": "Customer ID is required"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    /**
     * Timestamp when error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * HTTP status code.
     */
    private int status;

    /**
     * HTTP error name.
     */
    private String error;

    /**
     * Error message.
     */
    private String message;

    /**
     * Request path.
     */
    private String path;

    /**
     * Correlation ID for tracing.
     */
    private String correlationId;

    /**
     * Request ID.
     */
    private String requestId;

    /**
     * Validation errors.
     */
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    /**
     * Additional error details.
     */
    private Map<String, Object> details;

    /**
     * Validation error detail.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        /**
         * Field name with error.
         */
        private String field;

        /**
         * Error message.
         */
        private String message;

        /**
         * Rejected value.
         */
        private Object rejectedValue;
    }
}

