package com.adhar.kit.graphql.dataloader;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and helper for the DataLoader pattern to prevent N+1 query problems in GraphQL.
 *
 * <p>DataLoader batches and caches individual load requests so that multiple
 * fetches for the same type of resource within a single GraphQL request are
 * consolidated into a single batch call.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * DataLoaderRegistrar registrar = new DataLoaderRegistrar();
 * registrar.registerBatchLoader("users", keys ->
 *     CompletableFuture.supplyAsync(() -> userRepository.findAllById(keys))
 * );
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DataLoaderRegistrar {

    private final Map<String, BatchLoaderFunction<?, ?>> batchLoaders = new ConcurrentHashMap<>();

    /**
     * A function that loads a batch of values by their keys.
     *
     * <p>Implementations should accept a list of keys and return a {@link CompletableFuture}
     * containing a list of corresponding values in the same order.</p>
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    @FunctionalInterface
    public interface BatchLoaderFunction<K, V> {

        /**
         * Loads values for the given batch of keys.
         *
         * <p>The returned list must have the same size and order as the input keys.
         * If a value is not found for a key, the corresponding position should
         * contain {@code null}.</p>
         *
         * @param keys the list of keys to load
         * @return a future containing the list of values in the same order as keys
         */
        CompletableFuture<List<V>> load(List<K> keys);
    }

    /**
     * Registers a batch loader function under the given name.
     *
     * <p>If a loader with the same name already exists, it will be replaced.</p>
     *
     * @param name   the unique name for this data loader
     * @param loader the batch loader function
     * @param <K>    the key type
     * @param <V>    the value type
     * @throws IllegalArgumentException if name or loader is null
     */
    public <K, V> void registerBatchLoader(String name, BatchLoaderFunction<K, V> loader) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DataLoader name must not be null or blank");
        }
        if (loader == null) {
            throw new IllegalArgumentException("BatchLoaderFunction must not be null");
        }
        log.debug("Registering batch loader: {}", name);
        batchLoaders.put(name, loader);
    }

    /**
     * Returns the batch loader function registered under the given name.
     *
     * @param name the loader name
     * @param <K>  the key type
     * @param <V>  the value type
     * @return the batch loader function, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <K, V> BatchLoaderFunction<K, V> getBatchLoader(String name) {
        return (BatchLoaderFunction<K, V>) batchLoaders.get(name);
    }

    /**
     * Returns the set of all registered loader names.
     *
     * @return an unmodifiable set of loader names
     */
    public Set<String> getRegisteredNames() {
        return Set.copyOf(batchLoaders.keySet());
    }

    /**
     * Returns the number of registered batch loaders.
     *
     * @return the count of registered loaders
     */
    public int size() {
        return batchLoaders.size();
    }

    /**
     * Checks whether a loader with the given name is registered.
     *
     * @param name the loader name to check
     * @return true if a loader with that name exists
     */
    public boolean hasLoader(String name) {
        return batchLoaders.containsKey(name);
    }
}
