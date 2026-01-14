package com.adhar.kit.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lazy initialization utility for deferred object creation.
 *
 * <p>Creates objects only when first accessed, thread-safe.</p>
 *
 * <p><b>Example - Basic Usage:</b></p>
 * <pre>{@code
 * Lazy<ExpensiveResource> resource = Lazy.of(() -> new ExpensiveResource());
 *
 * // Not created yet
 *
 * ExpensiveResource instance = resource.get();  // Created now
 * ExpensiveResource same = resource.get();      // Same instance
 * }</pre>
 *
 * <p><b>Example - With Reset:</b></p>
 * <pre>{@code
 * Lazy<Connection> connection = Lazy.of(() -> createConnection());
 *
 * Connection conn = connection.get();  // Created
 * connection.reset();                   // Disposed
 * Connection newConn = connection.get(); // Re-created
 * }</pre>
 *
 * @param <T> the type of lazy-initialized value
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class Lazy<T> {

    private final Supplier<T> supplier;
    private volatile T value;
    private final Lock lock = new ReentrantLock();

    private Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * Creates a lazy initializer.
     *
     * @param supplier value supplier
     * @param <T> value type
     * @return lazy initializer
     */
    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    /**
     * Gets the value, creating it if necessary.
     *
     * @return the value
     */
    public T get() {
        if (value == null) {
            lock.lock();
            try {
                if (value == null) {
                    value = supplier.get();
                    log.debug("Lazy value initialized: {}", value.getClass().getSimpleName());
                }
            } finally {
                lock.unlock();
            }
        }
        return value;
    }

    /**
     * Checks if value has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return value != null;
    }

    /**
     * Resets the value, forcing re-initialization on next get().
     */
    public void reset() {
        lock.lock();
        try {
            value = null;
            log.debug("Lazy value reset");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets value if initialized, otherwise returns null.
     *
     * @return value or null
     */
    public T getIfInitialized() {
        return value;
    }
}

/**
 * Registry for managing multiple lazy-initialized values.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * LazyRegistry registry = new LazyRegistry();
 *
 * // Register lazy values
 * registry.register("db", () -> createDatabase());
 * registry.register("cache", () -> createCache());
 *
 * // Get values (created on first access)
 * Database db = registry.get("db", Database.class);
 * Cache cache = registry.get("cache", Cache.class);
 * }</pre>
 */
@Slf4j
class LazyRegistry {

    private final Map<String, Lazy<?>> registry = new ConcurrentHashMap<>();

    /**
     * Registers a lazy value.
     *
     * @param key registry key
     * @param supplier value supplier
     * @param <T> value type
     */
    public <T> void register(String key, Supplier<T> supplier) {
        registry.put(key, Lazy.of(supplier));
        log.debug("Registered lazy value: {}", key);
    }

    /**
     * Gets a value from registry.
     *
     * @param key registry key
     * @param type value type
     * @param <T> value type
     * @return the value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Lazy<?> lazy = registry.get(key);
        if (lazy == null) {
            throw new IllegalArgumentException("No lazy value registered for key: " + key);
        }
        return (T) lazy.get();
    }

    /**
     * Checks if key is registered.
     *
     * @param key registry key
     * @return true if registered
     */
    public boolean contains(String key) {
        return registry.containsKey(key);
    }

    /**
     * Checks if value is initialized.
     *
     * @param key registry key
     * @return true if initialized
     */
    public boolean isInitialized(String key) {
        Lazy<?> lazy = registry.get(key);
        return lazy != null && lazy.isInitialized();
    }

    /**
     * Resets a value.
     *
     * @param key registry key
     */
    public void reset(String key) {
        Lazy<?> lazy = registry.get(key);
        if (lazy != null) {
            lazy.reset();
        }
    }

    /**
     * Resets all values.
     */
    public void resetAll() {
        registry.values().forEach(Lazy::reset);
        log.debug("Reset all lazy values");
    }

    /**
     * Clears the registry.
     */
    public void clear() {
        registry.clear();
        log.debug("Cleared lazy registry");
    }
}

