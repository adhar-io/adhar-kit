package com.adhar.kit.dapr.pubsub;

import java.util.Map;

/**
 * Receives one CloudEvent of a topic, in the JSON shape Dapr delivers
 * ({@code id}, {@code source}, {@code type}, {@code specversion}, {@code datacontenttype},
 * {@code data}, {@code topic}, {@code pubsubname}, {@code traceparent}, ...), and answers with the
 * status Dapr acts on. A thrown exception counts as {@link DispatchStatus#RETRY}.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@FunctionalInterface
public interface TopicEventListener {

    /**
     * Handles one event.
     *
     * @param cloudEvent the event envelope
     * @return what Dapr should do with the message
     */
    DispatchStatus onEvent(Map<String, Object> cloudEvent);
}
