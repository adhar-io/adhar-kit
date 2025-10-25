package com.dbs.adhar.errorhandling.exception;

import org.springframework.http.HttpStatus;

public class AdharException extends RuntimeException {

    private final HttpStatus status;

    public AdharException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AdharException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

