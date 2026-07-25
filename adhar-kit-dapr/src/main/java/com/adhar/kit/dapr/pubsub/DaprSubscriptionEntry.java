package com.adhar.kit.dapr.pubsub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Subscription metadata entry returned by {@code GET /dapr/subscribe}, matching the shape
 * of a Dapr programmatic subscription:
 * <pre>{@code
 * [
 *   {
 *     "pubsubname": "pubsub",
 *     "topic": "orders.created",
 *     "route": "/dapr/subscribe/pubsub/orders-created"
 *   }
 * ]
 * }</pre>
 *
 * @param pubsubname     the pub/sub component name
 * @param topic          the topic name
 * @param route          the app route Dapr should POST CloudEvents to
 * @param metadata       optional subscription metadata (omitted when empty)
 * @param deadLetterTopic optional dead-letter topic (omitted when blank)
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DaprSubscriptionEntry(
        @JsonProperty("pubsubname") String pubsubname,
        String topic,
        String route,
        Map<String, String> metadata,
        String deadLetterTopic) {
}
