package com.adhar.kit.commons.exception;

/**
 * Exception thrown when a requested resource is not found.
 * This is typically used for 404 scenarios in REST APIs.
 */
public class ResourceNotFoundException extends AdharException {

    public static final String DEFAULT_ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(DEFAULT_ERROR_CODE, String.format("%s not found with id: %s", resourceType, resourceId));
    }

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceNotFoundException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Missing resources map to {@code 404} (Not Found).
     */
    @Override
    public int getHttpStatus() {
        return 404;
    }
}
