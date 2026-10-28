package com.adhar.kit.dapr.lock;

/**
 * Raised by {@code @DaprLock(failOnLockFailure = true)} when another holder has the lock.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprLockNotAcquiredException extends RuntimeException {

    private final String store;
    private final String resourceId;

    public DaprLockNotAcquiredException(String store, String resourceId) {
        super("Lock '" + resourceId + "' in store '" + store + "' is held by another owner");
        this.store = store;
        this.resourceId = resourceId;
    }

    public String getStore() {
        return store;
    }

    public String getResourceId() {
        return resourceId;
    }
}
