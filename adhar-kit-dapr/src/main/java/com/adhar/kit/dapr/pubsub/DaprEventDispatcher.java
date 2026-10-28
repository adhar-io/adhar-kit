package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.CloudEvent;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a delivered CloudEvent into a call of a subscriber and its outcome into the status Dapr
 * acts on, the same way for annotated methods and handlers registered in code:
 * <ul>
 *   <li>arguments: the payload converted to the parameter type, a {@link DaprCloudEvent}, or the
 *       SDK's {@link CloudEvent} (generic payload types honoured);</li>
 *   <li>results: {@code void}/{@code null} = SUCCESS, a {@link DispatchStatus} as given,
 *       {@code false} = RETRY;</li>
 *   <li>failures: {@link DropEventException} or unreadable data = DROP (redelivery cannot help),
 *       any other exception = RETRY.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprEventDispatcher {

    private final EventDataConverter converter;

    public DaprEventDispatcher() {
        this(new DaprJsonSerializer());
    }

    public DaprEventDispatcher(DaprJsonSerializer serializer) {
        this.converter = new EventDataConverter(serializer);
    }

    /**
     * @param objectMapper the mapper used to convert event data
     * @deprecated pass a {@link DaprJsonSerializer} so events convert like every other payload
     */
    @Deprecated(since = "0.2.0")
    public DaprEventDispatcher(ObjectMapper objectMapper) {
        this(new DaprJsonSerializer(objectMapper));
    }

    /**
     * Dispatches a CloudEvent envelope to a registered handler.
     *
     * @param handler    the handler
     * @param cloudEvent the CloudEvent envelope (deserialized JSON)
     * @return the dispatch result
     */
    public DispatchResult dispatch(DaprSubscriptionHandler handler, Map<String, Object> cloudEvent) {
        DispatchStatus status = handler.invoker().apply(cloudEvent == null ? Map.of() : cloudEvent);
        return new DispatchResult(status == null ? DispatchStatus.SUCCESS : status, null);
    }

    /**
     * The invoker of an annotated method.
     *
     * @param beanName the bean name (for logs)
     * @param bean     the bean
     * @param method   the method (zero or one parameter)
     * @return the invoker
     */
    public Function<Map<String, Object>, DispatchStatus> methodInvoker(String beanName, Object bean, Method method) {
        if (method.getParameterCount() > 1) {
            throw new IllegalStateException("Dapr subscriber " + beanName + "#" + method.getName()
                    + " must take at most one parameter (the event or its payload)");
        }
        Type parameterType = method.getParameterCount() == 0 ? null : method.getGenericParameterTypes()[0];
        method.setAccessible(true);
        return envelope -> {
            try {
                Object[] args = parameterType == null ? new Object[0] : new Object[] {argument(parameterType, envelope)};
                return statusOf(method.invoke(bean, args));
            } catch (InvocationTargetException e) {
                return failed(e.getTargetException(), envelope, beanName + "#" + method.getName());
            } catch (RuntimeException | IllegalAccessException e) {
                return failed(e, envelope, beanName + "#" + method.getName());
            }
        };
    }

    /**
     * The invoker of a handler registered in code.
     *
     * @param description what to call it in logs
     * @param type        the payload type
     * @param handler     the handler
     * @param <T>         the payload type
     * @return the invoker
     */
    public <T> Function<Map<String, Object>, DispatchStatus> handlerInvoker(String description, TypeRef<T> type,
                                                                           DaprTopicHandler<T> handler) {
        return envelope -> {
            try {
                @SuppressWarnings("unchecked")
                T data = (T) converter.data(envelope, type.getType());
                return statusOf(handler.handle(DaprCloudEvent.of(envelope, data)));
            } catch (Exception e) {
                return failed(e, envelope, description);
            }
        };
    }

    private Object argument(Type parameterType, Map<String, Object> envelope) {
        Class<?> raw = rawClass(parameterType);
        Type dataType = parameterType instanceof ParameterizedType parameterized
                ? parameterized.getActualTypeArguments()[0] : Object.class;
        if (raw == DaprCloudEvent.class) {
            return DaprCloudEvent.of(envelope, converter.data(envelope, dataType));
        }
        if (raw == CloudEvent.class) {
            CloudEvent<Object> event = new CloudEvent<>();
            event.setId(text(envelope, "id"));
            event.setSource(text(envelope, "source"));
            event.setType(text(envelope, "type"));
            event.setSpecversion(text(envelope, "specversion"));
            event.setDatacontenttype(text(envelope, "datacontenttype"));
            event.setPubsubName(text(envelope, "pubsubname"));
            event.setTopic(text(envelope, "topic"));
            event.setTraceParent(text(envelope, "traceparent"));
            event.setTraceState(text(envelope, "tracestate"));
            event.setData(converter.data(envelope, dataType));
            return event;
        }
        return converter.data(envelope, parameterType);
    }

    private static DispatchStatus failed(Throwable failure, Map<String, Object> envelope, String handler) {
        if (failure instanceof DropEventException) {
            log.warn("Dropping event {} on topic {} in {}: {}", envelope.get("id"), envelope.get("topic"), handler,
                    failure.getMessage());
            return DispatchStatus.DROP;
        }
        log.warn("{} failed on event {} (topic {}); asking Dapr to redeliver", handler, envelope.get("id"),
                envelope.get("topic"), failure);
        return DispatchStatus.RETRY;
    }

    static DispatchStatus statusOf(Object result) {
        if (result instanceof DispatchStatus status) {
            return status;
        }
        if (result instanceof Boolean ok) {
            return ok ? DispatchStatus.SUCCESS : DispatchStatus.RETRY;
        }
        if (result instanceof String text) {
            try {
                return DispatchStatus.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return DispatchStatus.SUCCESS;
            }
        }
        return DispatchStatus.SUCCESS;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> c) {
            return c;
        }
        return Object.class;
    }

    private static String text(Map<String, Object> envelope, String key) {
        Object value = envelope.get(key);
        return value == null ? null : value.toString();
    }
}
