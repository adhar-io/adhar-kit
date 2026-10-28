package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.DaprSubscription;
import io.dapr.utils.TypeRef;

/**
 * Where {@code dapr.subscribe(...)} registers a handler when the application serves Dapr's app
 * callbacks (a Spring MVC app): the handler is then advertised on {@code GET /dapr/subscribe}
 * like an annotated one. Without it the facade falls back to a streaming subscription.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public interface ProgrammaticSubscriptions {

    /**
     * Registers a handler.
     *
     * @param topic   the topic
     * @param type    the payload type
     * @param handler the handler
     * @param options the options, with the pub/sub component resolved
     * @param <T>     the payload type
     * @return the subscription
     */
    <T> DaprSubscription register(String topic, TypeRef<T> type, DaprTopicHandler<T> handler, SubscribeOptions options);
}
