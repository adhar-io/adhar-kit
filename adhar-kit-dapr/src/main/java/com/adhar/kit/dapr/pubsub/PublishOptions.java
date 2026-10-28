package com.adhar.kit.dapr.pubsub;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for a publish: Dapr publish metadata and the payload content type. Immutable.
 *
 * <p>The {@code cloudevent.*} keys override attributes of the CloudEvent envelope the sidecar
 * builds, which is how the kit stamps {@code id}, {@code source} and {@code type} and carries the
 * caller's trace context:</p>
 * <pre>{@code
 * dapr.publishEvent("pubsub", "orders", order,
 *     PublishOptions.none().withCloudEvent("order-42", "orders-service", "io.adhar.orders.order.created"));
 * }</pre>
 *
 * @param pubsub      the pub/sub component, or {@code null} for the service default ({@code pubsub})
 * @param metadata    publish metadata
 * @param contentType the payload content type, or {@code null} for JSON
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record PublishOptions(String pubsub, Map<String, String> metadata, String contentType) {

    /** CloudEvent attribute overrides understood by the sidecar. */
    public static final String CLOUDEVENT_ID = "cloudevent.id";
    public static final String CLOUDEVENT_SOURCE = "cloudevent.source";
    public static final String CLOUDEVENT_TYPE = "cloudevent.type";
    public static final String CLOUDEVENT_TRACEPARENT = "cloudevent.traceparent";
    public static final String CLOUDEVENT_TRACESTATE = "cloudevent.tracestate";
    /** Publish the payload as-is, without a CloudEvent envelope. */
    public static final String RAW_PAYLOAD = "rawPayload";
    /** Message time-to-live in seconds. */
    public static final String TTL_IN_SECONDS = "ttlInSeconds";
    /** Partition/ordering key for brokers that support one (Kafka). */
    public static final String PARTITION_KEY = "partitionKey";

    private static final PublishOptions NONE = new PublishOptions(null, Map.of(), null);

    public PublishOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static PublishOptions none() {
        return NONE;
    }

    public static PublishOptions of(Map<String, String> metadata) {
        return new PublishOptions(null, metadata, null);
    }

    /** Publishes to {@code pubsubName} instead of the default component. */
    public PublishOptions withPubsub(String pubsubName) {
        return new PublishOptions(pubsubName, metadata, contentType);
    }

    public PublishOptions withMetadata(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        copy.put(key, value);
        return new PublishOptions(pubsub, copy, contentType);
    }

    public PublishOptions withContentType(String newContentType) {
        return new PublishOptions(pubsub, metadata, newContentType);
    }

    /** Sets the CloudEvent {@code id}, {@code source} and {@code type}; {@code null}s are skipped. */
    public PublishOptions withCloudEvent(String id, String source, String type) {
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        putIfText(copy, CLOUDEVENT_ID, id);
        putIfText(copy, CLOUDEVENT_SOURCE, source);
        putIfText(copy, CLOUDEVENT_TYPE, type);
        return new PublishOptions(pubsub, copy, contentType);
    }

    public PublishOptions withTtl(Duration ttl) {
        long seconds = Math.max(1L, ttl.toSeconds() + (ttl.toNanosPart() > 0 ? 1 : 0));
        return withMetadata(TTL_IN_SECONDS, String.valueOf(seconds));
    }

    public PublishOptions withPartitionKey(String key) {
        return withMetadata(PARTITION_KEY, key);
    }

    public PublishOptions rawPayload() {
        return withMetadata(RAW_PAYLOAD, "true");
    }

    private static void putIfText(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
