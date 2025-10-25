package com.adhar.kit.commons.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response structure for API error responses.
 * Provides detailed error information including validation errors and field-specific errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private String details;
    private List<String> validationErrors;
    private Map<String, String> fieldErrors;
    private String requestId;
    private String path;
    private LocalDateTime timestamp;

    /**
     * Creates a simple error response with code and message.
     *
     * @param code the error code
     * @param message the error message
     * @return ErrorResponse instance
     */
    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with validation errors.
     *
     * @param code the error code
     * @param message the error message
     * @param validationErrors list of validation error messages
     * @return ErrorResponse instance
     */
    public static ErrorResponse ofValidationErrors(String code, String message, List<String> validationErrors) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .validationErrors(validationErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with field-specific errors.
     *
     * @param code the error code
     * @param message the error message
     * @param fieldErrors map of field names to error messages
     * @return ErrorResponse instance
     */
    public static ErrorResponse ofFieldErrors(String code, String message, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Sets the request ID for tracking purposes.
     *
     * @param requestId the request ID
     * @return this ErrorResponse for method chaining
     */
    public ErrorResponse withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * Sets the request path.
     *
     * @param path the request path
     * @return this ErrorResponse for method chaining
     */
    public ErrorResponse withPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * Sets additional error details.
     *
     * @param details the error details
     * @return this ErrorResponse for method chaining
     */
    public ErrorResponse withDetails(String details) {
        this.details = details;
        return this;
    }
}
