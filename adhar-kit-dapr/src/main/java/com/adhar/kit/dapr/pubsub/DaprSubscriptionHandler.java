package com.adhar.kit.dapr.pubsub;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Internal registration record describing a discovered {@code @DaprSubscribe}/{@code @DaprTopic}
 * handler method: the route it is dispatched under, and the bean/method to invoke.
 *
 * @param route          the synthesized dispatch route (always under {@code /dapr/subscribe/...})
 * @param pubsubName     the pub/sub component name
 * @param topic          the topic name
 * @param deadLetterTopic optional dead-letter topic, or {@code null}
 * @param metadata       subscription metadata
 * @param beanName       the Spring bean name owning the handler method
 * @param target         the bean instance the handler method is invoked on
 * @param method         the handler method itself
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record DaprSubscriptionHandler(
        String route,
        String pubsubName,
        String topic,
        String deadLetterTopic,
        Map<String, String> metadata,
        String beanName,
        Object target,
        Method method) {
}
