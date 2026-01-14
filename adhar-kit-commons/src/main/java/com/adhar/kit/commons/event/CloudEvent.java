package com.adhar.kit.commons.event;

import lombok.Builder;
import lombok.Data;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic CloudEvent wrapper following CloudEvents 1.0 specification.
 * Provides a generic container for event data with CloudEvents metadata.
 *
 * @param <T> the type of the event data
 */
@Data
@Builder
public class CloudEvent<T> {

    /**
     * CloudEvents specification version.
     */
    @Builder.Default
    private String specVersion = "1.0";

    /**
     * Unique identifier for this event.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The source URI identifying the context in which the event occurred.
     */
    private URI source;

    /**
     * The type of event (e.g., "com.adhar.order.created").
     */
    private String type;

    /**
     * The subject of the event in the context of the source (optional).
     */
    private String subject;

    /**
     * The timestamp when the event occurred.
     */
    @Builder.Default
    private Instant time = Instant.now();

    /**
     * The content type of the data.
     */
    @Builder.Default
    private String dataContentType = "application/json";

    /**
     * The schema URI of the data (optional).
     */
    private URI dataSchema;

    /**
     * The event data.
     */
    private T data;

    /**
     * CloudEvent extensions (custom attributes).
     */
    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();

    /**
     * Adds an extension attribute.
     *
     * @param key the extension key
     * @param value the extension value
     * @return this CloudEvent instance for chaining
     */
    public CloudEvent<T> extensionAttribute(String key, Object value) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        extensions.put(key, value);
        return this;
    }

    /**
     * Gets an extension attribute.
     *
     * @param key the extension key
     * @return the extension value, or null if not found
     */
    public Object getExtensionAttribute(String key) {
        return extensions != null ? extensions.get(key) : null;
    }
}

