package com.adhar.kit.dapr;

import java.util.Map;
import java.util.Objects;

/**
 * Secrets from one secret store (the default {@code secretstore} unless {@link #withStore} picks
 * another).
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprSecretOperations {

    private final DaprFacade dapr;
    private final String store;

    DaprSecretOperations(DaprFacade dapr, String store) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public String store() {
        return store;
    }

    public DaprSecretOperations withStore(String storeName) {
        return new DaprSecretOperations(dapr, storeName);
    }

    /** Every key/value pair of a secret. */
    public Map<String, String> get(String name) {
        return dapr.getSecretValues(store, name);
    }

    public Map<String, String> get(String name, Map<String, String> metadata) {
        return dapr.getSecretValues(store, name, metadata);
    }

    /** The value of a single-value secret (or its entry named like the secret). */
    public String value(String name) {
        return dapr.getSecret(store, name);
    }

    /** Every secret the store allows this app to read. */
    public Map<String, Map<String, String>> getBulk() {
        return dapr.getBulkSecretsNested(store);
    }
}
