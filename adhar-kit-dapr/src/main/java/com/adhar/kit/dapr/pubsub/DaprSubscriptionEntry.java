package com.adhar.kit.dapr.pubsub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * One programmatic subscription as returned by {@code GET /dapr/subscribe}: every handler of a
 * (pub/sub, topic) pair folds into one entry, a single handler as {@code route}, several as
 * routing {@code rules} plus a {@code default}.
 * <pre>{@code
 * [
 *   {"pubsubname": "pubsub", "topic": "orders", "route": "/dapr/events/pubsub/orders",
 *    "deadLetterTopic": "orders-dlq", "bulkSubscribe": {"enabled": true, "maxMessagesCount": 100}}
 * ]
 * }</pre>
 *
 * @param pubsubname      the pub/sub component name
 * @param topic           the topic name
 * @param route           the route of a topic with no routing rules
 * @param routes          the routing rules and default route, when there are rules
 * @param metadata        subscription metadata (omitted when empty)
 * @param deadLetterTopic dead-letter topic (omitted when blank)
 * @param bulkSubscribe   bulk delivery settings (omitted when off)
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DaprSubscriptionEntry(
        @JsonProperty("pubsubname") String pubsubname,
        String topic,
        String route,
        Routes routes,
        Map<String, String> metadata,
        String deadLetterTopic,
        BulkSubscribe bulkSubscribe) {

    /** A topic with one route (the shape most entries have). */
    public DaprSubscriptionEntry(String pubsubname, String topic, String route, Map<String, String> metadata,
                                 String deadLetterTopic) {
        this(pubsubname, topic, route, null, metadata, deadLetterTopic, null);
    }

    /**
     * Routing rules ({@code match} is a CEL expression over the CloudEvent) and the default route.
     *
     * @param rules        the rules, in priority order
     * @param defaultRoute the route of events no rule matches, or {@code null}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Routes(List<Rule> rules, @JsonProperty("default") String defaultRoute) {
    }

    /**
     * One routing rule.
     *
     * @param match the CEL expression
     * @param path  the route of matching events
     */
    public record Rule(String match, String path) {
    }

    /**
     * Bulk delivery settings.
     *
     * @param enabled            always {@code true} when present
     * @param maxMessagesCount   batch size limit, or {@code null} for Dapr's default
     * @param maxAwaitDurationMs batch wait, or {@code null} for Dapr's default
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BulkSubscribe(boolean enabled, Integer maxMessagesCount, Integer maxAwaitDurationMs) {
    }
}
