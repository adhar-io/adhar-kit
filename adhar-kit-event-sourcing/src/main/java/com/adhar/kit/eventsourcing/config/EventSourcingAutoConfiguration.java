package com.adhar.kit.eventsourcing.config;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.store.EventEntryRepository;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import com.adhar.kit.eventsourcing.store.JpaEventStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for the Adhar Event Sourcing module.
 *
 * <p>Registers an {@link EventStore}, {@link EventBus}, and
 * {@link AggregateRepository} based on the configured event store type.
 * When JPA is on the classpath and {@code adhar.event-sourcing.event-store-type}
 * is set to {@code jpa}, the JPA-backed store is used; otherwise an in-memory
 * store is provided.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(EventSourcingProperties.class)
@ConditionalOnProperty(prefix = "adhar.event-sourcing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventSourcingAutoConfiguration {

    @PostConstruct
    public void logEventSourcingConfiguration() {
        log.info("Adhar Event Sourcing module initialized");
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBus eventBus() {
        return new SimpleEventBus();
    }

    @Bean
    @ConditionalOnMissingBean
    public AggregateRepository aggregateRepository(EventStore eventStore, EventBus eventBus) {
        return new AggregateRepository(eventStore, eventBus);
    }

    /**
     * JPA-based event store configuration, activated when JPA is on the classpath
     * and event-store-type is "jpa".
     */
    @Configuration
    @ConditionalOnClass(name = "jakarta.persistence.EntityManager")
    @ConditionalOnProperty(prefix = "adhar.event-sourcing", name = "event-store-type", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackages = "com.adhar.kit.eventsourcing.store")
    static class JpaEventStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EventStore eventStore(EventEntryRepository repository) {
            log.info("Using JPA-backed event store");
            return new JpaEventStore(repository);
        }
    }

    /**
     * In-memory event store configuration, activated when event-store-type is "in-memory".
     */
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.event-sourcing", name = "event-store-type", havingValue = "in-memory")
    static class InMemoryEventStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EventStore eventStore() {
            log.info("Using in-memory event store (development/testing only)");
            return new InMemoryEventStore();
        }
    }
}
