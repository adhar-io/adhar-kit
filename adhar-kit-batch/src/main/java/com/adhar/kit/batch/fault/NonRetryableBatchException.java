package com.adhar.kit.batch.fault;

/**
 * A failure that retrying the same item cannot fix: invalid data, a broken invariant, a
 * reconciliation mismatch.
 *
 * <p>The kit's retry presets never retry it, nor any exception whose cause chain contains it.
 * A step may still skip it when a {@link FaultTolerance} declares it skippable. A run that fails
 * with it is a non-retryable failure: the Kubernetes runner exits with code 3, which the
 * platform's pod failure policy fails without another attempt (design 0025 §5.7).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class NonRetryableBatchException extends RuntimeException {

    /**
     * @param message what is wrong
     */
    public NonRetryableBatchException(String message) {
        super(message);
    }

    /**
     * @param message what is wrong
     * @param cause   the underlying failure
     */
    public NonRetryableBatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
