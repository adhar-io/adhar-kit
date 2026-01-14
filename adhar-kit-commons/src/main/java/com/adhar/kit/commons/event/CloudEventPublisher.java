package com.adhar.kit.commons.event;

/**
 * Interface for publishing CloudEvents.
 *
 * <p>This is a framework-agnostic interface. Each framework module
 * (Spring, Quarkus, Micronaut) will provide its own implementation.</p>
 *
 * <p><b>Spring Implementation Example:</b></p>
 * <pre>{@code
 * @Component
 * public class SpringCloudEventPublisher implements CloudEventPublisher {
 *
 *     private final ApplicationEventPublisher publisher;
 *
 *     @Override
 *     public <T> void publish(CloudEvent<T> event) {
 *         publisher.publishEvent(event);
 *     }
 *
 *     @Override
 *     public <T> void publishAsync(CloudEvent<T> event) {
 *         CompletableFuture.runAsync(() -> publisher.publishEvent(event));
 *     }
 * }
 * }</pre>
 *
 * <p><b>Kafka Implementation Example:</b></p>
 * <pre>{@code
 * @Component
 * public class KafkaCloudEventPublisher implements CloudEventPublisher {
 *
 *     private final KafkaTemplate<String, CloudEvent<?>> kafkaTemplate;
 *
 *     @Override
 *     public <T> void publish(CloudEvent<T> event) {
 *         kafkaTemplate.send("events", event.getId(), event);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface CloudEventPublisher {

    /**
     * Publishes a CloudEvent synchronously.
     *
     * @param event the CloudEvent to publish
     * @param <T> event data type
     */
    <T> void publish(CloudEvent<T> event);

    /**
     * Publishes a CloudEvent asynchronously.
     *
     * @param event the CloudEvent to publish
     * @param <T> event data type
     */
    default <T> void publishAsync(CloudEvent<T> event) {
        java.util.concurrent.CompletableFuture.runAsync(() -> publish(event));
    }

    /**
     * Publishes a CloudEvent to a specific topic/channel.
     *
     * @param topic the topic/channel
     * @param event the CloudEvent to publish
     * @param <T> event data type
     */
    default <T> void publish(String topic, CloudEvent<T> event) {
        publish(event);
    }

    /**
     * Publishes a domain event.
     *
     * @param domainEvent the domain event
     * @param source the event source URI
     */
    default void publishDomainEvent(DomainEvent domainEvent, java.net.URI source) {
        CloudEvent<DomainEvent> cloudEvent = domainEvent.toCloudEvent(source);
        publish(cloudEvent);
    }
}

