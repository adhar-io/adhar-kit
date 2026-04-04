package com.adhar.kit.commons.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight CloudEvent envelope following the CloudEvents 1.0 specification.
 *
 * <p>This record provides a minimal, dependency-free CloudEvent wrapper suitable
 * for use across all Adhar Kit modules. Unlike {@link CloudEvent} (which uses Lombok)
 * or {@link BaseCloudEvent} (which requires subclassing), this record is immutable
 * by design and offers convenient factory methods for quick event creation.</p>
 *
 * <p><b>Example - Creating a CloudEvent:</b></p>
 * <pre>{@code
 * // Simple creation with source, type, and data
 * AdharCloudEvent<OrderData> event = AdharCloudEvent.of(
 *     "adhar-kit/persistence",
 *     "com.adhar.entity.created",
 *     orderData
 * );
 *
 * // With subject for additional context
 * AdharCloudEvent<OrderData> event = AdharCloudEvent.of(
 *     "adhar-kit/persistence",
 *     "com.adhar.entity.created",
 *     "order-123",
 *     orderData
 * );
 * }</pre>
 *
 * @param id              unique identifier for this event (UUID)
 * @param source          the source identifying the context in which the event occurred
 * @param type            the type of event (e.g., "com.adhar.entity.created")
 * @param specVersion     the CloudEvents specification version ("1.0")
 * @param time            the timestamp when the event occurred
 * @param subject         optional subject providing additional context
 * @param dataContentType the content type of the data (e.g., "application/json")
 * @param data            the event payload
 * @param <T>             the type of the event data
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 */
public record AdharCloudEvent<T>(
        String id,
        String source,
        String type,
        String specVersion,
        Instant time,
        String subject,
        String dataContentType,
        T data
) {

    /**
     * Creates an {@code AdharCloudEvent} with auto-generated id, spec version, timestamp,
     * and default content type. No subject is set.
     *
     * @param source the event source (e.g., "adhar-kit/persistence")
     * @param type   the event type (e.g., "com.adhar.entity.created")
     * @param data   the event payload
     * @param <T>    the type of the event data
     * @return a new {@code AdharCloudEvent} instance
     */
    public static <T> AdharCloudEvent<T> of(String source, String type, T data) {
        return new AdharCloudEvent<>(
                UUID.randomUUID().toString(),
                source,
                type,
                "1.0",
                Instant.now(),
                null,
                "application/json",
                data
        );
    }

    /**
     * Creates an {@code AdharCloudEvent} with auto-generated id, spec version, timestamp,
     * default content type, and a subject for additional context.
     *
     * @param source  the event source (e.g., "adhar-kit/persistence")
     * @param type    the event type (e.g., "com.adhar.entity.created")
     * @param subject the event subject (e.g., "order-123")
     * @param data    the event payload
     * @param <T>     the type of the event data
     * @return a new {@code AdharCloudEvent} instance
     */
    public static <T> AdharCloudEvent<T> of(String source, String type, String subject, T data) {
        return new AdharCloudEvent<>(
                UUID.randomUUID().toString(),
                source,
                type,
                "1.0",
                Instant.now(),
                subject,
                "application/json",
                data
        );
    }
}
