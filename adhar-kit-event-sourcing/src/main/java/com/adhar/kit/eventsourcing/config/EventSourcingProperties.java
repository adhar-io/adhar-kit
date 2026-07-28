package com.adhar.kit.eventsourcing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Event Sourcing module.
 *
 * <p><b>Configuration example:</b></p>
 * <pre>{@code
 * adhar:
 *   event-sourcing:
 *     enabled: true
 *     snapshot-interval: 100
 *     event-store-type: jpa
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "adhar.event-sourcing")
public class EventSourcingProperties {

    /**
     * Whether event sourcing support is enabled.
     */
    private boolean enabled = true;

    /**
     * Number of events after which a snapshot is taken.
     */
    private int snapshotInterval = 100;

    /**
     * The type of event store to use. Supported values: "jpa", "in-memory".
     */
    private String eventStoreType = "jpa";

    /**
     * Maximum number of attempts {@code RetryingAggregateRepository} makes when a save
     * fails due to an optimistic concurrency conflict.
     */
    private int retryMaxAttempts = 3;

    /**
     * Kafka event bus settings. Only relevant when {@code spring-kafka} is on the classpath and
     * {@link Kafka#isEnabled() enabled}.
     */
    private final Kafka kafka = new Kafka();

    /**
     * Settings for the Kafka-backed {@link com.adhar.kit.eventsourcing.bus.EventBus}.
     */
    @Getter
    @Setter
    public static class Kafka {

        /**
         * Whether the Kafka event bus should be used in place of the in-process bus. Requires a
         * {@code KafkaTemplate} bean to be available.
         */
        private boolean enabled = false;

        /**
         * The topic domain events are published to and consumed from.
         */
        private String topic = "adhar.event-sourcing.events";

        /**
         * The consumer group id used by the event bus listener.
         */
        private String groupId = "adhar-event-sourcing";
    }
}
