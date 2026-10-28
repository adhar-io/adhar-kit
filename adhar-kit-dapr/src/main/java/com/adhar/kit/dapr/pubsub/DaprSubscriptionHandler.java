package com.adhar.kit.dapr.pubsub;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * A registered subscriber: where Dapr delivers (route), what it subscribes to, and how an
 * envelope reaches the handler.
 *
 * @param route           the route Dapr POSTs events to ({@code /dapr/events/<pubsub>/<topic>} by default)
 * @param pubsubName      the pub/sub component name
 * @param topic           the topic name
 * @param deadLetterTopic dead-letter topic, or {@code null}
 * @param metadata        subscription metadata
 * @param match           CEL routing rule, or {@code null} for the topic's default route
 * @param priority        rule priority (lower first) when {@code match} is set
 * @param bulk            bulk delivery settings, or {@code null}
 * @param beanName        the owning bean, or a description for code registrations
 * @param target          the bean instance, or {@code null} for code registrations
 * @param method          the handler method, or {@code null} for code registrations
 * @param invoker         dispatches one envelope to the handler
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record DaprSubscriptionHandler(
        String route,
        String pubsubName,
        String topic,
        String deadLetterTopic,
        Map<String, String> metadata,
        String match,
        int priority,
        SubscribeOptions.BulkSubscribe bulk,
        String beanName,
        Object target,
        Method method,
        Function<Map<String, Object>, DispatchStatus> invoker) {

    public DaprSubscriptionHandler {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** {@code true} when Dapr delivers the payload without a CloudEvent envelope. */
    public boolean rawPayload() {
        return "true".equalsIgnoreCase(metadata.get("rawPayload"));
    }
}
