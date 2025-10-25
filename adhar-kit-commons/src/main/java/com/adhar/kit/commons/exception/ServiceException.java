package com.adhar.kit.commons.exception;

/**
 * Exception thrown for service-level errors and business logic failures.
 * This is typically used for internal service errors that are not validation-related.
 */
public class ServiceException extends AdharException {

    public static final String DEFAULT_ERROR_CODE = "SERVICE_ERROR";

    public ServiceException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ServiceException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public ServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public ServiceException(String errorCode, String message, Object... args) {
        super(errorCode, message, args);
    }

    public ServiceException(String errorCode, String message, Throwable cause, Object... args) {
        super(errorCode, message, cause, args);
    }
}
