package com.adhar.kit.commons.exception;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown for validation errors.
 * This is typically used for input validation failures in REST APIs.
 */
public class ValidationException extends AdharException {

    public static final String DEFAULT_ERROR_CODE = "VALIDATION_ERROR";

    private final List<String> validationErrors;
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(DEFAULT_ERROR_CODE, message);
        this.validationErrors = null;
        this.fieldErrors = null;
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(DEFAULT_ERROR_CODE, message);
        this.validationErrors = validationErrors;
        this.fieldErrors = null;
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(DEFAULT_ERROR_CODE, message);
        this.validationErrors = null;
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
        this.validationErrors = null;
        this.fieldErrors = null;
    }

    public ValidationException(String errorCode, String message, List<String> validationErrors) {
        super(errorCode, message);
        this.validationErrors = validationErrors;
        this.fieldErrors = null;
    }

    public ValidationException(String errorCode, String message, Map<String, String> fieldErrors) {
        super(errorCode, message);
        this.validationErrors = null;
        this.fieldErrors = fieldErrors;
    }

    /**
     * Validation failures map to {@code 400} (Bad Request).
     */
    @Override
    public int getHttpStatus() {
        return 400;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
