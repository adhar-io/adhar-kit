package com.adhar.kit.dapr;

import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateQueryResult;
import com.adhar.kit.dapr.state.StateRepository;
import io.dapr.utils.TypeRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * State on one store (the default {@code statestore} unless {@link #withStore} picks another),
 * with the method names every adhar-kit language uses: {@code get}, {@code getBulk}, {@code save},
 * {@code saveBulk}, {@code delete}, {@code transact}, {@code query}.
 *
 * <pre>{@code
 * Cart cart = dapr.state().get("cart:42", Cart.class);
 * dapr.state().save("cart:42", cart, SaveOptions.etag(etag).withTtl(Duration.ofDays(7)));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprStateOperations {

    private final DaprFacade dapr;
    private final String store;

    DaprStateOperations(DaprFacade dapr, String store) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /** The state store these operations use. */
    public String store() {
        return store;
    }

    /** The same operations on another state store. */
    public DaprStateOperations withStore(String storeName) {
        return new DaprStateOperations(dapr, storeName);
    }

    public <T> T get(String key, Class<T> type) {
        return dapr.getState(store, key, type);
    }

    public <T> T get(String key, TypeRef<T> type) {
        return dapr.getState(store, key, type);
    }

    public <T> T get(String key, Class<T> type, GetOptions options) {
        return dapr.getState(store, key, type, options);
    }

    /** The value with its ETag, for a conditional save. */
    public <T> StateEntry<T> getEntry(String key, Class<T> type) {
        return dapr.getStateEntry(store, key, TypeRef.get(type), GetOptions.none());
    }

    public <T> List<StateEntry<T>> getBulk(List<String> keys, Class<T> type) {
        return dapr.getBulkStateEntries(store, keys, TypeRef.get(type));
    }

    public <T> List<StateEntry<T>> getBulk(List<String> keys, TypeRef<T> type) {
        return dapr.getBulkStateEntries(store, keys, type);
    }

    public void save(String key, Object value) {
        dapr.saveState(store, key, value);
    }

    public void save(String key, Object value, SaveOptions options) {
        dapr.saveState(store, key, value, options);
    }

    public void saveBulk(Map<String, ?> values) {
        dapr.saveBulkState(store, values);
    }

    public void saveBulk(List<StateItem> items) {
        dapr.saveBulkState(store, items);
    }

    public void delete(String key) {
        dapr.deleteState(store, key);
    }

    public void delete(String key, DeleteOptions options) {
        dapr.deleteState(store, key, options);
    }

    /** Applies upserts and deletes atomically. */
    public void transact(List<StateOperation> operations) {
        dapr.executeStateTransaction(store, operations);
    }

    public void transact(List<StateOperation> operations, Map<String, String> metadata) {
        dapr.executeStateTransaction(store, operations, metadata);
    }

    /** Runs a state query (Dapr alpha) and returns one page. */
    public <T> StateQueryResult<T> query(String query, Class<T> type) {
        return dapr.queryStatePage(store, query, TypeRef.get(type), Map.of());
    }

    public <T> StateQueryResult<T> query(String query, TypeRef<T> type) {
        return dapr.queryStatePage(store, query, type, Map.of());
    }

    /** A typed, ETag-aware repository over this store. */
    public <T> StateRepository<T> repository(Class<T> type) {
        return new StateRepository<>(dapr, store, type);
    }
}
