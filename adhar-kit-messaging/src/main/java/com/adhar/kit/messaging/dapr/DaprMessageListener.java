package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.DropEventException;
import com.adhar.kit.dapr.pubsub.EventDataConverter;
import com.adhar.kit.messaging.core.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link MessageListener} on Dapr pub/sub: each {@code subscribe} opens a Dapr streaming
 * subscription (a runtime subscription the sidecar learns about immediately, unlike the
 * programmatic ones it reads at startup), so the SPI's subscribe/unsubscribe/pause/resume map
 * directly. In Dapr's memory mode the subscription receives this process's own publishes.
 *
 * <p><b>Contract mapping:</b></p>
 * <ul>
 *   <li><b>Source:</b> the Dapr topic; the pub/sub component is fixed per listener
 *       ({@code adhar.messaging.dapr.pubsub-name}, default the service's {@code adhar.dapr.pubsub}).</li>
 *   <li><b>Group:</b> Dapr groups consumers by app id, so every replica of a service shares a
 *       subscription and each message reaches one of them; the SPI's group is not used.</li>
 *   <li><b>Headers:</b> the CloudEvent attributes, as {@code ce-id}, {@code ce-type},
 *       {@code ce-source}, {@code ce-specversion}, {@code ce-datacontenttype}, {@code ce-time},
 *       {@code ce-subject}, plus {@code traceparent}, {@code tracestate}, {@code topic} and
 *       {@code pubsubname}.</li>
 *   <li><b>Acknowledgement:</b> {@code true} (or a plain consumer returning) acknowledges,
 *       {@code false} or an exception asks Dapr to redeliver, data that cannot be read as the
 *       requested type is dropped.</li>
 *   <li><b>Pause/resume:</b> close the stream and open it again; messages published meanwhile
 *       stay with the broker.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprMessageListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DaprMessageListener.class);

    private final DaprFacade daprFacade;
    private final String pubsubName;
    private final EventDataConverter converter;
    private final Map<String, Registration<?>> registrations = new ConcurrentHashMap<>();

    /**
     * @param daprFacade the Dapr facade
     * @param pubsubName the pub/sub component (must not be blank)
     */
    public DaprMessageListener(DaprFacade daprFacade, String pubsubName) {
        if (daprFacade == null) {
            throw new IllegalArgumentException("daprFacade must not be null");
        }
        if (!StringUtils.hasText(pubsubName)) {
            throw new IllegalArgumentException("pubsubName must not be null or empty");
        }
        this.daprFacade = daprFacade;
        this.pubsubName = pubsubName;
        this.converter = new EventDataConverter(daprFacade.serializer());
    }

    @Override
    public <T> String subscribe(String source, Class<T> type, Consumer<T> consumer) {
        return register(source, type, (payload, headers) -> {
            consumer.accept(payload);
            return true;
        });
    }

    @Override
    public <T> String subscribe(String source, String group, Class<T> type, Consumer<T> consumer) {
        return subscribe(source, type, consumer);
    }

    @Override
    public <T> String subscribeWithHeaders(String source, Class<T> type, BiConsumer<T, Map<String, Object>> consumer) {
        return register(source, type, (payload, headers) -> {
            consumer.accept(payload, headers);
            return true;
        });
    }

    @Override
    public <T> String subscribeWithHeaders(String source, String group, Class<T> type,
                                           BiConsumer<T, Map<String, Object>> consumer) {
        return subscribeWithHeaders(source, type, consumer);
    }

    @Override
    public <T> String subscribeWithAck(String source, Class<T> type, Function<T, Boolean> consumer) {
        return register(source, type, (payload, headers) -> consumer.apply(payload));
    }

    @Override
    public <T> String subscribeWithAck(String source, String group, Class<T> type, Function<T, Boolean> consumer) {
        return subscribeWithAck(source, type, consumer);
    }

    @Override
    public <T> String subscribeWithHeadersAndAck(String source, Class<T> type,
                                                 BiFunction<T, Map<String, Object>, Boolean> consumer) {
        return register(source, type, consumer);
    }

    @Override
    public <T> String subscribeWithHeadersAndAck(String source, String group, Class<T> type,
                                                 BiFunction<T, Map<String, Object>, Boolean> consumer) {
        return subscribeWithHeadersAndAck(source, type, consumer);
    }

    @Override
    public boolean unsubscribe(String consumerId) {
        Registration<?> registration = registrations.remove(consumerId);
        if (registration == null) {
            return false;
        }
        registration.close();
        return true;
    }

    @Override
    public boolean pause(String consumerId) {
        Registration<?> registration = registrations.get(consumerId);
        if (registration == null || registration.subscription == null) {
            return false;
        }
        registration.close();
        return true;
    }

    @Override
    public boolean resume(String consumerId) {
        Registration<?> registration = registrations.get(consumerId);
        if (registration == null || registration.subscription != null) {
            return false;
        }
        registration.open();
        return true;
    }

    private <T> String register(String topic, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> handler) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("source (the Dapr topic) must not be empty");
        }
        String id = UUID.randomUUID().toString();
        Registration<T> registration = new Registration<>(topic, type, handler);
        registration.open();
        registrations.put(id, registration);
        log.debug("Subscribed to Dapr {}/{} as consumer {}", pubsubName, topic, id);
        return id;
    }

    private final class Registration<T> {
        private final String topic;
        private final Class<T> type;
        private final BiFunction<T, Map<String, Object>, Boolean> handler;
        private volatile DaprSubscription subscription;

        private Registration(String topic, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> handler) {
            this.topic = topic;
            this.type = type;
            this.handler = handler;
        }

        synchronized void open() {
            subscription = daprFacade.subscribeStreaming(pubsubName, topic, null, Map.of(), this::deliver);
        }

        synchronized void close() {
            DaprSubscription current = subscription;
            subscription = null;
            if (current != null) {
                current.unsubscribe();
            }
        }

        private DispatchStatus deliver(Map<String, Object> event) {
            T payload;
            try {
                payload = type.cast(converter.data(event, type));
            } catch (DropEventException e) {
                log.warn("Dropping message {} on {}/{}: {}", event.get("id"), pubsubName, topic, e.getMessage());
                return DispatchStatus.DROP;
            }
            try {
                Boolean acknowledged = handler.apply(payload, headers(event));
                return Boolean.FALSE.equals(acknowledged) ? DispatchStatus.RETRY : DispatchStatus.SUCCESS;
            } catch (RuntimeException e) {
                log.warn("Consumer of {}/{} failed on message {}; asking Dapr to redeliver", pubsubName, topic,
                        event.get("id"), e);
                return DispatchStatus.RETRY;
            }
        }
    }

    private static Map<String, Object> headers(Map<String, Object> event) {
        Map<String, Object> headers = new LinkedHashMap<>();
        copy(event, "id", headers, "ce-id");
        copy(event, "type", headers, "ce-type");
        copy(event, "source", headers, "ce-source");
        copy(event, "specversion", headers, "ce-specversion");
        copy(event, "datacontenttype", headers, "ce-datacontenttype");
        copy(event, "time", headers, "ce-time");
        copy(event, "subject", headers, "ce-subject");
        copy(event, "traceparent", headers, "traceparent");
        copy(event, "tracestate", headers, "tracestate");
        copy(event, "topic", headers, "topic");
        copy(event, "pubsubname", headers, "pubsubname");
        return headers;
    }

    private static void copy(Map<String, Object> from, String key, Map<String, Object> to, String header) {
        Object value = from.get(key);
        if (value != null) {
            to.put(header, value);
        }
    }
}
