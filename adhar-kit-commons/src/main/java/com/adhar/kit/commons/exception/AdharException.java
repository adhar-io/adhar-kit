package com.adhar.kit.commons.exception;

import lombok.Getter;

/**
 * Base exception class for all Adhar platform exceptions.
 * This is the root exception that all other custom exceptions in the platform should extend.
 */
@Getter
public class AdharException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public AdharException(String message) {
        super(message);
        this.errorCode = null;
        this.args = null;
    }

    public AdharException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.args = null;
    }

    public AdharException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public AdharException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    public AdharException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public AdharException(String errorCode, String message, Throwable cause, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * Returns the HTTP status code this exception maps to when translated to an API response.
     *
     * <p>Defaults to {@code 500} (Internal Server Error). Subclasses override this to provide
     * their natural HTTP status (e.g. {@code 404} for {@link ResourceNotFoundException}).</p>
     *
     * @return the HTTP status code
     */
    public int getHttpStatus() {
        return 500;
    }
}
