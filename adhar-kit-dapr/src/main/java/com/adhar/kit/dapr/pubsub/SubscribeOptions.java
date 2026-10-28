package com.adhar.kit.dapr.pubsub;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for a subscription registered in code.
 *
 * @param pubsub          the pub/sub component, or {@code null} for the service default
 * @param route           the HTTP route Dapr delivers to, or {@code null} for
 *                        {@code /dapr/events/<pubsub>/<topic>}
 * @param deadLetterTopic where Dapr sends messages it gave up on, or {@code null}
 * @param metadata        subscription metadata (for example {@code rawPayload=true})
 * @param bulk            bulk delivery settings, or {@code null} for one message per request
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record SubscribeOptions(String pubsub, String route, String deadLetterTopic, Map<String, String> metadata,
                               BulkSubscribe bulk) {

    private static final SubscribeOptions NONE = new SubscribeOptions(null, null, null, Map.of(), null);

    public SubscribeOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SubscribeOptions none() {
        return NONE;
    }

    public SubscribeOptions withPubsub(String newPubsub) {
        return new SubscribeOptions(newPubsub, route, deadLetterTopic, metadata, bulk);
    }

    public SubscribeOptions withRoute(String newRoute) {
        return new SubscribeOptions(pubsub, newRoute, deadLetterTopic, metadata, bulk);
    }

    public SubscribeOptions withDeadLetterTopic(String topic) {
        return new SubscribeOptions(pubsub, route, topic, metadata, bulk);
    }

    public SubscribeOptions withMetadata(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        copy.put(key, value);
        return new SubscribeOptions(pubsub, route, deadLetterTopic, copy, bulk);
    }

    public SubscribeOptions withBulk(int maxMessagesCount, int maxAwaitDurationMs) {
        return new SubscribeOptions(pubsub, route, deadLetterTopic, metadata,
                new BulkSubscribe(maxMessagesCount, maxAwaitDurationMs));
    }

    /**
     * Bulk delivery: Dapr batches up to {@code maxMessagesCount} messages or waits up to
     * {@code maxAwaitDurationMs}, and the kit answers a status per message.
     *
     * @param maxMessagesCount   batch size limit ({@code <= 0} for Dapr's default)
     * @param maxAwaitDurationMs how long Dapr waits to fill a batch ({@code < 0} for Dapr's default)
     */
    public record BulkSubscribe(int maxMessagesCount, int maxAwaitDurationMs) {
    }
}
