package com.adhar.kit.dapr;

import com.adhar.kit.dapr.lock.UnlockStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Distributed locks on one lock store (the default {@code lockstore} unless {@link #withStore}
 * picks another).
 *
 * <pre>{@code
 * Optional<Report> report = dapr.lock().runLocked("daily-report", instanceId, 300, reports::generate);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public final class DaprLockOperations {

    private final DaprFacade dapr;
    private final String store;

    DaprLockOperations(DaprFacade dapr, String store) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public String store() {
        return store;
    }

    public DaprLockOperations withStore(String storeName) {
        return new DaprLockOperations(dapr, storeName);
    }

    /** Takes the lock for {@code expirySeconds} if nobody holds it; never waits. */
    public boolean tryLock(String resourceId, String owner, int expirySeconds) {
        return dapr.tryLock(store, resourceId, owner, expirySeconds);
    }

    public UnlockStatus unlock(String resourceId, String owner) {
        return dapr.unlockStatus(store, resourceId, owner);
    }

    /**
     * Runs {@code work} while holding the lock and releases it afterwards; empty when someone else
     * holds the lock (the work does not run).
     */
    public <T> Optional<T> runLocked(String resourceId, String owner, int expirySeconds, Supplier<T> work) {
        if (!tryLock(resourceId, owner, expirySeconds)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(work.get());
        } finally {
            try {
                unlock(resourceId, owner);
            } catch (DaprError e) {
                // The lock expires on its own; the work's result still stands.
                log.warn("Releasing lock '{}' in store '{}' failed: {}", resourceId, store, e.getMessage());
            }
        }
    }
}
