package com.adhar.kit.commons.exception;

import lombok.Getter;

/**
 * Global exception handler for common exception types.
 *
 * <p>Provides centralized exception handling with proper HTTP status mapping.</p>
 *
 * <p><b>Example - Spring:</b></p>
 * <pre>{@code
 * @ControllerAdvice
 * public class GlobalExceptionHandler extends AdharExceptionHandler {
 *     // Additional custom handlers
 * }
 * }</pre>
 *
 * <p><b>Example - Quarkus:</b></p>
 * <pre>{@code
 * @Provider
 * public class GlobalExceptionMapper extends AdharExceptionHandler
 *         implements ExceptionMapper<Exception> {
 *     // Quarkus-specific implementation
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
public abstract class AdharExceptionHandler {

    /**
     * Handles AdharException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleAdharException(AdharException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code(ex.getErrorCode() != null ? ex.getErrorCode() : "ADHAR_ERROR")
            .message(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles ValidationException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleValidationException(ValidationException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .validationErrors(ex.getValidationErrors())
            .fieldErrors(ex.getFieldErrors())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles ResourceNotFoundException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles ServiceException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleServiceException(ServiceException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles generic exceptions.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleGenericException(Exception ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .details(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles IllegalArgumentException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleIllegalArgumentException(
            IllegalArgumentException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code("BAD_REQUEST")
            .message("Invalid argument")
            .details(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Handles NullPointerException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleNullPointerException(
            NullPointerException ex) {
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("Null value encountered")
            .details(ex.getMessage())
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }
}

