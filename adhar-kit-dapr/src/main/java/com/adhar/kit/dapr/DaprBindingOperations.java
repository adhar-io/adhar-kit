package com.adhar.kit.dapr;

import com.adhar.kit.dapr.bindings.BindingResponse;

import java.util.Map;
import java.util.Objects;

/**
 * Output bindings: send data to an external system through a binding component and read what it
 * answered.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprBindingOperations {

    private final DaprFacade dapr;

    DaprBindingOperations(DaprFacade dapr) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
    }

    public BindingResponse invoke(String name, String operation, Object data) {
        return dapr.invokeBindingWithResponse(name, operation, data, Map.of());
    }

    public BindingResponse invoke(String name, String operation, Object data, Map<String, String> metadata) {
        return dapr.invokeBindingWithResponse(name, operation, data, metadata);
    }

    /** Invokes and reads the response data as JSON of {@code responseType}. */
    public <T> T invoke(String name, String operation, Object data, Map<String, String> metadata,
                        Class<T> responseType) {
        return dapr.invokeBinding(name, operation, data, metadata, responseType);
    }
}
