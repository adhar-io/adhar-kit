package com.adhar.kit.dapr.lock;

/**
 * Result of releasing a distributed lock, as Dapr reports it.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum UnlockStatus {

    /** The lock was held by the owner and is now released. */
    SUCCESS,

    /** No lock existed (it expired, or was never taken). */
    LOCK_DOES_NOT_EXIST,

    /** Someone else holds the lock; it was left in place. */
    LOCK_BELONGS_TO_OTHERS,

    /** The lock store failed. */
    INTERNAL_ERROR;

    /**
     * Converts the SDK's status.
     *
     * @param status the SDK status
     * @return the kit status
     */
    public static UnlockStatus of(io.dapr.client.domain.UnlockResponseStatus status) {
        if (status == null) {
            return INTERNAL_ERROR;
        }
        return switch (status) {
            case SUCCESS -> SUCCESS;
            case LOCK_UNEXIST -> LOCK_DOES_NOT_EXIST;
            case LOCK_BELONG_TO_OTHERS -> LOCK_BELONGS_TO_OTHERS;
            default -> INTERNAL_ERROR;
        };
    }
}
