package com.adhar.kit.dapr.invoke;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One service invocation request through the sidecar: verb, body, headers, query and timeout.
 * Immutable; every {@code with*} returns a copy.
 *
 * <pre>{@code
 * InvokeResponse response = dapr.invoke("inventory", "api/v1/items",
 *     InvokeOptions.get().withQuery("sku", "A-1").withHeader("API-Version", "1.2.0"));
 * Order order = dapr.invoke("orders", "api/v1/orders", InvokeOptions.post(request), Order.class);
 * }</pre>
 *
 * @param verb        the HTTP verb
 * @param body        the body ({@code byte[]} and {@code String} are sent as-is, anything else as JSON),
 *                    or {@code null}
 * @param contentType the body's content type, or {@code null} for the default of the body
 * @param headers     request headers
 * @param query       query parameters (each may repeat)
 * @param timeout     per-call timeout, or {@code null} for the configured default
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record InvokeOptions(HttpVerb verb, Object body, String contentType, Map<String, String> headers,
                            Map<String, List<String>> query, Duration timeout) {

    public InvokeOptions {
        verb = verb == null ? HttpVerb.POST : verb;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        if (query == null) {
            query = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            query.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            query = java.util.Collections.unmodifiableMap(copy);
        }
    }

    public static InvokeOptions of(HttpVerb verb) {
        return new InvokeOptions(verb, null, null, Map.of(), Map.of(), null);
    }

    public static InvokeOptions get() {
        return of(HttpVerb.GET);
    }

    public static InvokeOptions delete() {
        return of(HttpVerb.DELETE);
    }

    public static InvokeOptions post(Object body) {
        return of(HttpVerb.POST).withBody(body);
    }

    public static InvokeOptions put(Object body) {
        return of(HttpVerb.PUT).withBody(body);
    }

    public static InvokeOptions patch(Object body) {
        return of(HttpVerb.PATCH).withBody(body);
    }

    public InvokeOptions withBody(Object newBody) {
        return new InvokeOptions(verb, newBody, contentType, headers, query, timeout);
    }

    public InvokeOptions withContentType(String newContentType) {
        return new InvokeOptions(verb, body, newContentType, headers, query, timeout);
    }

    public InvokeOptions withHeader(String name, String value) {
        Map<String, String> copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new InvokeOptions(verb, body, contentType, copy, query, timeout);
    }

    public InvokeOptions withQuery(String name, String value) {
        Map<String, List<String>> copy = new LinkedHashMap<>(query);
        List<String> values = new ArrayList<>(copy.getOrDefault(name, List.of()));
        values.add(value);
        copy.put(name, values);
        return new InvokeOptions(verb, body, contentType, headers, copy, timeout);
    }

    public InvokeOptions withTimeout(Duration newTimeout) {
        return new InvokeOptions(verb, body, contentType, headers, query, newTimeout);
    }
}
