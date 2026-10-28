package com.adhar.kit.dapr.pubsub;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A CloudEvent delivered to a subscriber, with its {@code data} already converted to the
 * handler's type. The same fields exist in every adhar-kit language.
 *
 * @param id              event id (unique per message; use it to de-duplicate)
 * @param source          who produced it (Dapr sets the publishing app id)
 * @param type            event type
 * @param specversion     CloudEvents version
 * @param datacontenttype content type of {@code data}
 * @param subject         subject, or {@code null}
 * @param time            event time as sent, or {@code null}
 * @param topic           the topic it arrived on
 * @param pubsubname      the pub/sub component it arrived through
 * @param traceparent     W3C trace parent of the publisher, or {@code null}
 * @param tracestate      W3C trace state, or {@code null}
 * @param data            the payload
 * @param extensions      any other attributes
 * @param <T>             the payload type
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record DaprCloudEvent<T>(String id, String source, String type, String specversion,
                                String datacontenttype, String subject, String time, String topic,
                                String pubsubname, String traceparent, String tracestate, T data,
                                Map<String, Object> extensions) {

    private static final Set<String> KNOWN = Set.of("id", "source", "type", "specversion", "datacontenttype",
            "subject", "time", "topic", "pubsubname", "traceparent", "tracestate", "data", "data_base64");

    public DaprCloudEvent {
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    /**
     * Builds the event from the delivered envelope and the already-converted data.
     *
     * @param envelope the CloudEvent as delivered
     * @param data     the converted payload
     * @param <T>      the payload type
     * @return the event
     */
    public static <T> DaprCloudEvent<T> of(Map<String, Object> envelope, T data) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        envelope.forEach((key, value) -> {
            if (!KNOWN.contains(key) && value != null) {
                extensions.put(key, value);
            }
        });
        return new DaprCloudEvent<>(text(envelope, "id"), text(envelope, "source"), text(envelope, "type"),
                text(envelope, "specversion"), text(envelope, "datacontenttype"), text(envelope, "subject"),
                text(envelope, "time"), text(envelope, "topic"), text(envelope, "pubsubname"),
                text(envelope, "traceparent"), text(envelope, "tracestate"), data, extensions);
    }

    private static String text(Map<String, Object> envelope, String key) {
        Object value = envelope.get(key);
        return value == null ? null : value.toString();
    }
}
