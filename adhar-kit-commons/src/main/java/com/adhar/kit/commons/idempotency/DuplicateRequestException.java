package com.adhar.kit.commons.idempotency;

import com.adhar.kit.commons.exception.AdharException;
import lombok.Getter;

/**
 * Thrown by {@link IdempotencyAspect} when a duplicate call arrives while the original
 * call with the same idempotency key is still in flight.
 *
 * <p>Maps to HTTP {@code 409} (Conflict); clients should retry after the original
 * request has completed.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
public class DuplicateRequestException extends AdharException {

    public static final String DEFAULT_ERROR_CODE = "DUPLICATE_REQUEST";

    private final String idempotencyKey;

    /**
     * Constructor with the offending idempotency key.
     *
     * @param idempotencyKey the key that is already in flight
     */
    public DuplicateRequestException(String idempotencyKey) {
        super(DEFAULT_ERROR_CODE,
            "A request with idempotency key '" + idempotencyKey + "' is already in progress");
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Duplicate in-flight requests map to {@code 409} (Conflict).
     */
    @Override
    public int getHttpStatus() {
        return 409;
    }
}
