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
}
