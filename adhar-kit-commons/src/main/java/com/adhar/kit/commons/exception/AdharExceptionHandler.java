package com.adhar.kit.commons.exception;

import com.adhar.kit.commons.i18n.ErrorCatalog;
import lombok.Getter;

/**
 * Global exception handler for common exception types.
 *
 * <p>Provides centralized exception handling with proper HTTP status mapping.
 * When an {@link ErrorCatalog} is set (done automatically by the module
 * auto-configuration), error messages are resolved through it so responses are
 * localized to the current request locale; the exception's own message is used
 * as fallback when the catalog has no entry for the error code.</p>
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

    /** Optional catalog used to localize error messages; {@code null} disables localization. */
    private ErrorCatalog errorCatalog;

    /**
     * Sets the error catalog used to resolve localized error messages.
     *
     * @param errorCatalog the catalog, or {@code null} to disable localization
     */
    public void setErrorCatalog(ErrorCatalog errorCatalog) {
        this.errorCatalog = errorCatalog;
    }

    /**
     * Resolves the localized message for an error code through the configured
     * {@link ErrorCatalog}, falling back to the given default when no catalog
     * is set or the catalog has no entry for the code.
     *
     * @param errorCode the error code
     * @param defaultMessage fallback message
     * @param args optional message arguments
     * @return localized message or the fallback
     */
    protected String resolveMessage(String errorCode, String defaultMessage, Object... args) {
        if (errorCatalog == null || errorCode == null) {
            return defaultMessage;
        }
        return errorCatalog.resolveOrDefault(errorCode, defaultMessage, args);
    }

    /**
     * Handles AdharException.
     *
     * @param ex the exception
     * @return error response
     */
    protected com.adhar.kit.commons.model.ErrorResponse handleAdharException(AdharException ex) {
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : "ADHAR_ERROR";
        return com.adhar.kit.commons.model.ErrorResponse.builder()
            .code(code)
            .message(resolveMessage(code, ex.getMessage(), ex.getArgs()))
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
            .message(resolveMessage(ex.getErrorCode(), ex.getMessage(), ex.getArgs()))
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
            .message(resolveMessage(ex.getErrorCode(), ex.getMessage(), ex.getArgs()))
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
            .message(resolveMessage(ex.getErrorCode(), ex.getMessage(), ex.getArgs()))
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
            .message(resolveMessage("INTERNAL_SERVER_ERROR", "An unexpected error occurred"))
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
            .message(resolveMessage("BAD_REQUEST", "Invalid argument"))
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

