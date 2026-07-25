package com.adhar.kit.dapr.state;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Typed, ETag-aware repository over {@link DaprFacade#getStateWithETag} and
 * {@link DaprFacade#saveStateWithETag}, adding an optimistic-concurrency retry loop for
 * read-modify-write updates.
 *
 * <pre>{@code
 * StateRepository<Cart> carts = new StateRepository<>(DaprFacade.getInstance(), "statestore", Cart.class);
 *
 * Cart cart = carts.update("cart:" + userId, existing -> {
 *     Cart c = existing != null ? existing : new Cart(userId);
 *     c.addItem(product);
 *     return c;
 * });
 * }</pre>
 *
 * @param <T> the state value type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class StateRepository<T> {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final DaprFacade daprFacade;
    private final String storeName;
    private final Class<T> type;
    private final int maxRetries;

    public StateRepository(DaprFacade daprFacade, String storeName, Class<T> type) {
        this(daprFacade, storeName, type, DEFAULT_MAX_RETRIES);
    }

    public StateRepository(DaprFacade daprFacade, String storeName, Class<T> type, int maxRetries) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.storeName = Objects.requireNonNull(storeName, "storeName must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be >= 1");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Finds the value for a key, ignoring its ETag.
     */
    public Optional<T> find(String key) {
        return Optional.ofNullable(daprFacade.getStateWithETag(storeName, key, type).getValue());
    }

    /**
     * Finds the value and ETag for a key.
     */
    public StateWithETag<T> findWithETag(String key) {
        return daprFacade.getStateWithETag(storeName, key, type);
    }

    /**
     * Unconditionally saves a value (no ETag check).
     */
    public void save(String key, T value) {
        daprFacade.saveState(storeName, key, value);
    }

    /**
     * Reads the current value (with ETag), applies {@code updater}, and saves the result with
     * an ETag-guarded write. On an ETag conflict (another writer won the race), the read-apply-
     * save cycle is retried up to {@code maxRetries} times with a short backoff.
     *
     * @param key     the state key
     * @param updater receives the current value (possibly {@code null} if absent) and returns
     *                the new value to persist
     * @return the updated value that was successfully saved
     * @throws OptimisticConcurrencyException if all retries are exhausted due to ETag conflicts
     */
    public T update(String key, UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater must not be null");
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            StateWithETag<T> current = daprFacade.getStateWithETag(storeName, key, type);
            T updated = updater.apply(current.getValue());
            if (daprFacade.saveStateWithETag(storeName, key, updated, current.getEtag())) {
                return updated;
            }
            log.debug("Optimistic concurrency conflict saving key '{}' (attempt {}/{}); retrying",
                    key, attempt, maxRetries);
            backoff(attempt);
        }
        throw new OptimisticConcurrencyException(
                "Failed to update state for key '" + key + "' after " + maxRetries
                        + " attempt(s) due to concurrent modification");
    }

    /**
     * Deletes a key using an ETag-guarded delete (read-then-delete). Returns {@code false} if
     * the key does not currently exist.
     */
    public boolean delete(String key) {
        StateWithETag<T> current = daprFacade.getStateWithETag(storeName, key, type);
        if (current.getValue() == null) {
            return false;
        }
        return daprFacade.deleteStateWithETag(storeName, key, current.getEtag());
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(20L * attempt, 200L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
