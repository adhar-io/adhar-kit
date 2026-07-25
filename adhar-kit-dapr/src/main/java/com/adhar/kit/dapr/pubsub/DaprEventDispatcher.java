package com.adhar.kit.dapr.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.CloudEvent;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Routes an incoming CloudEvent payload (as delivered by the Dapr sidecar to
 * {@code POST /dapr/subscribe/...}) to the target {@code @DaprSubscribe}/{@code @DaprTopic}
 * handler method, converting the event's {@code data} field to the handler's parameter type
 * (or wrapping the whole envelope in an {@link CloudEvent} when the handler declares one).
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprEventDispatcher {

    private final ObjectMapper objectMapper;

    public DaprEventDispatcher() {
        this(new ObjectMapper());
    }

    public DaprEventDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Dispatches a CloudEvent envelope to the given handler.
     *
     * @param handler    the resolved handler
     * @param cloudEvent the CloudEvent envelope (as a generic map, e.g. deserialized JSON)
     * @return the dispatch result
     */
    public DispatchResult dispatch(DaprSubscriptionHandler handler, Map<String, Object> cloudEvent) {
        try {
            Object[] args = resolveArguments(handler.method(), cloudEvent);
            handler.method().setAccessible(true);
            handler.method().invoke(handler.target(), args);
            return DispatchResult.success();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            log.warn("Handler {}#{} threw an exception processing topic '{}'",
                    handler.beanName(), handler.method().getName(), handler.topic(), cause);
            return DispatchResult.retry(cause);
        } catch (Exception e) {
            log.warn("Failed to dispatch event to handler {}#{} for topic '{}'",
                    handler.beanName(), handler.method().getName(), handler.topic(), e);
            return DispatchResult.retry(e);
        }
    }

    private Object[] resolveArguments(Method method, Map<String, Object> cloudEvent) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        Map<String, Object> event = cloudEvent != null ? cloudEvent : Map.of();
        Class<?> paramType = paramTypes[0];
        Object arg = CloudEvent.class.isAssignableFrom(paramType)
                ? buildCloudEvent(event)
                : objectMapper.convertValue(event.containsKey("data") ? event.get("data") : event, paramType);
        return new Object[] {arg};
    }

    private CloudEvent<Object> buildCloudEvent(Map<String, Object> raw) {
        CloudEvent<Object> event = new CloudEvent<>();
        event.setId(asString(raw.get("id")));
        event.setSource(asString(raw.get("source")));
        event.setType(asString(raw.get("type")));
        event.setSpecversion(asString(raw.get("specversion")));
        event.setDatacontenttype(asString(raw.get("datacontenttype")));
        event.setPubsubName(asString(raw.get("pubsubname")));
        event.setTopic(asString(raw.get("topic")));
        event.setData(raw.get("data"));
        return event;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
