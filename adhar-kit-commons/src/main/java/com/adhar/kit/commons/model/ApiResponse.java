package com.adhar.kit.commons.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper for all REST endpoints.
 * Provides consistent structure for success and error responses.
 *
 * @param <T> the type of the response data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private ErrorResponse error;
    private String requestId;
    private LocalDateTime timestamp;

    /**
     * Creates a successful response with data.
     *
     * @param data the response data
     * @param <T> the type of the data
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates a successful response with data and message.
     *
     * @param data the response data
     * @param message the success message
     * @param <T> the type of the data
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates a successful response with just a message (no data).
     *
     * @param message the success message
     * @return ApiResponse with success=true
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response.
     *
     * @param error the error details
     * @return ApiResponse with success=false
     */
    public static ApiResponse<Void> error(ErrorResponse error) {
        return ApiResponse.<Void>builder()
                .success(false)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with message.
     *
     * @param message the error message
     * @return ApiResponse with success=false
     */
    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with code and message.
     *
     * @param errorCode the error code
     * @param message the error message
     * @return ApiResponse with success=false
     */
    public static ApiResponse<Void> error(String errorCode, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ApiResponse.<Void>builder()
                .success(false)
                .error(errorResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Sets the request ID for tracking purposes.
     *
     * @param requestId the request ID
     * @return this ApiResponse for method chaining
     */
    public ApiResponse<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}
