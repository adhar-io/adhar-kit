package com.adhar.kit.eventsourcing.config;

import com.adhar.kit.eventsourcing.bus.DomainEventKafkaSerde;
import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.KafkaEventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.projection.InMemoryProjectionCheckpointStore;
import com.adhar.kit.eventsourcing.projection.JpaProjectionCheckpointStore;
import com.adhar.kit.eventsourcing.projection.ProjectionCheckpointEntryRepository;
import com.adhar.kit.eventsourcing.projection.ProjectionCheckpointStore;
import com.adhar.kit.eventsourcing.projection.ProjectionManager;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.repository.RetryingAggregateRepository;
import com.adhar.kit.eventsourcing.saga.InMemorySagaStateStore;
import com.adhar.kit.eventsourcing.saga.JpaSagaStateStore;
import com.adhar.kit.eventsourcing.saga.SagaInstanceEntryRepository;
import com.adhar.kit.eventsourcing.saga.SagaManager;
import com.adhar.kit.eventsourcing.saga.SagaStateStore;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.adhar.kit.eventsourcing.serialization.JacksonEventSerializer;
import com.adhar.kit.eventsourcing.snapshot.InMemorySnapshotStore;
import com.adhar.kit.eventsourcing.snapshot.JpaSnapshotStore;
import com.adhar.kit.eventsourcing.snapshot.SnapshotEntryRepository;
import com.adhar.kit.eventsourcing.snapshot.SnapshotStore;
import com.adhar.kit.eventsourcing.store.EventEntryRepository;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import com.adhar.kit.eventsourcing.store.JpaEventStore;
import com.adhar.kit.eventsourcing.upcast.EventUpcaster;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

/**
 * Auto-configuration for the Adhar Event Sourcing module.
 *
 * <p>Registers an {@link EventStore}, {@link EventBus}, {@link AggregateRepository},
 * {@link SnapshotStore}, {@link ProjectionCheckpointStore}, {@link ProjectionManager} and
 * supporting serialization/upcasting beans based on the configured event store type. When
 * JPA is on the classpath and {@code adhar.event-sourcing.event-store-type} is set to
 * {@code jpa}, JPA-backed stores are used; otherwise in-memory stores are provided.</p>
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
    public DomainEventKafkaSerde domainEventKafkaSerde(ObjectMapper objectMapper, EventTypeRegistry eventTypeRegistry) {
        return new DomainEventKafkaSerde(objectMapper, eventTypeRegistry);
    }

    /**
     * Kafka-backed {@link EventBus}, used in place of the in-process bus when {@code spring-kafka}
     * is on the classpath, a {@link KafkaTemplate} bean exists, and
     * {@code adhar.event-sourcing.kafka.enabled=true}. Declared before {@link #eventBus()} so its
     * presence backs off the default in-process bus via {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(EventBus.class)
    @ConditionalOnProperty(prefix = "adhar.event-sourcing.kafka", name = "enabled", havingValue = "true")
    public EventBus kafkaEventBus(KafkaTemplate<String, String> kafkaTemplate, DomainEventKafkaSerde serde,
                                  EventSourcingProperties properties) {
        log.info("Using Kafka-backed event bus (topic '{}')", properties.getKafka().getTopic());
        return new KafkaEventBus(kafkaTemplate, serde, properties.getKafka().getTopic());
    }

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus eventBus() {
        return new SimpleEventBus();
    }

    @Bean
    @ConditionalOnMissingBean
    public UpcasterChain upcasterChain(List<EventUpcaster> upcasters) {
        return new UpcasterChain(upcasters);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventTypeRegistry eventTypeRegistry() {
        return new EventTypeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper eventSourcingObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public JacksonEventSerializer jacksonEventSerializer(ObjectMapper objectMapper, EventTypeRegistry eventTypeRegistry) {
        return new JacksonEventSerializer(objectMapper, eventTypeRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AggregateRepository aggregateRepository(EventStore eventStore, EventBus eventBus,
                                                    SnapshotStore snapshotStore, EventSourcingProperties properties) {
        return new AggregateRepository(eventStore, eventBus, snapshotStore, properties.getSnapshotInterval());
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryingAggregateRepository retryingAggregateRepository(AggregateRepository aggregateRepository,
                                                                     EventSourcingProperties properties) {
        return new RetryingAggregateRepository(aggregateRepository, properties.getRetryMaxAttempts());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectionManager projectionManager(EventBus eventBus, ProjectionCheckpointStore checkpointStore) {
        return new ProjectionManager(eventBus, checkpointStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaManager sagaManager(SagaStateStore sagaStateStore, EventBus eventBus) {
        return new SagaManager(sagaStateStore, eventBus);
    }

    /**
     * JPA-based store configuration, activated when JPA is on the classpath and
     * event-store-type is "jpa".
     */
    @Configuration
    @ConditionalOnClass(name = "jakarta.persistence.EntityManager")
    @ConditionalOnProperty(prefix = "adhar.event-sourcing", name = "event-store-type", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackages = "com.adhar.kit.eventsourcing")
    static class JpaEventStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EventStore eventStore(EventEntryRepository repository, UpcasterChain upcasterChain) {
            log.info("Using JPA-backed event store");
            return new JpaEventStore(repository, upcasterChain);
        }

        @Bean
        @ConditionalOnMissingBean
        public SnapshotStore snapshotStore(SnapshotEntryRepository repository) {
            log.info("Using JPA-backed snapshot store");
            return new JpaSnapshotStore(repository);
        }

        @Bean
        @ConditionalOnMissingBean
        public ProjectionCheckpointStore projectionCheckpointStore(ProjectionCheckpointEntryRepository repository) {
            log.info("Using JPA-backed projection checkpoint store");
            return new JpaProjectionCheckpointStore(repository);
        }

        @Bean
        @ConditionalOnMissingBean
        public SagaStateStore sagaStateStore(SagaInstanceEntryRepository repository, ObjectMapper objectMapper) {
            log.info("Using JPA-backed saga state store");
            return new JpaSagaStateStore(repository, objectMapper);
        }
    }

    /**
     * In-memory store configuration, activated when event-store-type is "in-memory".
     */
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.event-sourcing", name = "event-store-type", havingValue = "in-memory")
    static class InMemoryEventStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EventStore eventStore(UpcasterChain upcasterChain) {
            log.info("Using in-memory event store (development/testing only)");
            return new InMemoryEventStore(upcasterChain);
        }

        @Bean
        @ConditionalOnMissingBean
        public SnapshotStore snapshotStore() {
            log.info("Using in-memory snapshot store (development/testing only)");
            return new InMemorySnapshotStore();
        }

        @Bean
        @ConditionalOnMissingBean
        public ProjectionCheckpointStore projectionCheckpointStore() {
            log.info("Using in-memory projection checkpoint store (development/testing only)");
            return new InMemoryProjectionCheckpointStore();
        }

        @Bean
        @ConditionalOnMissingBean
        public SagaStateStore sagaStateStore() {
            log.info("Using in-memory saga state store (development/testing only)");
            return new InMemorySagaStateStore();
        }
    }
}
