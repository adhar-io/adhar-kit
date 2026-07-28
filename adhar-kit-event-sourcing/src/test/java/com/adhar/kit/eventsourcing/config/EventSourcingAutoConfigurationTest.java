package com.adhar.kit.eventsourcing.config;

import com.adhar.kit.eventsourcing.bus.DomainEventKafkaSerde;
import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.KafkaEventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.projection.ProjectionCheckpointStore;
import com.adhar.kit.eventsourcing.projection.ProjectionManager;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.repository.RetryingAggregateRepository;
import com.adhar.kit.eventsourcing.saga.InMemorySagaStateStore;
import com.adhar.kit.eventsourcing.saga.SagaManager;
import com.adhar.kit.eventsourcing.saga.SagaStateStore;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.adhar.kit.eventsourcing.serialization.JacksonEventSerializer;
import com.adhar.kit.eventsourcing.snapshot.InMemorySnapshotStore;
import com.adhar.kit.eventsourcing.snapshot.SnapshotStore;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("EventSourcingAutoConfiguration")
class EventSourcingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventSourcingAutoConfiguration.class));

    @Test
    @DisplayName("in-memory event-store-type wires in-memory stores and all supporting beans")
    void inMemoryConfigurationWiresAllBeans() {
        contextRunner
                .withPropertyValues("adhar.event-sourcing.event-store-type=in-memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventBus.class);
                    assertThat(context).hasSingleBean(EventStore.class);
                    assertThat(context.getBean(EventStore.class)).isInstanceOf(InMemoryEventStore.class);
                    assertThat(context).hasSingleBean(SnapshotStore.class);
                    assertThat(context.getBean(SnapshotStore.class)).isInstanceOf(InMemorySnapshotStore.class);
                    assertThat(context).hasSingleBean(ProjectionCheckpointStore.class);
                    assertThat(context).hasSingleBean(ProjectionManager.class);
                    assertThat(context).hasSingleBean(AggregateRepository.class);
                    assertThat(context).hasSingleBean(RetryingAggregateRepository.class);
                    assertThat(context).hasSingleBean(UpcasterChain.class);
                    assertThat(context).hasSingleBean(EventTypeRegistry.class);
                    assertThat(context).hasSingleBean(JacksonEventSerializer.class);
                    assertThat(context).hasSingleBean(EventSourcingProperties.class);
                    assertThat(context).hasSingleBean(DomainEventKafkaSerde.class);
                    assertThat(context).hasSingleBean(SagaManager.class);
                    assertThat(context).hasSingleBean(SagaStateStore.class);
                    assertThat(context.getBean(SagaStateStore.class)).isInstanceOf(InMemorySagaStateStore.class);
                    // Without a KafkaTemplate bean the in-process bus is used by default.
                    assertThat(context.getBean(EventBus.class)).isInstanceOf(SimpleEventBus.class);
                });
    }

    @Test
    @DisplayName("Kafka event bus replaces the in-process bus when enabled and a KafkaTemplate is present")
    void kafkaEventBusSelectedWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.event-sourcing.event-store-type=in-memory",
                        "adhar.event-sourcing.kafka.enabled=true",
                        "adhar.event-sourcing.kafka.topic=my.events")
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(EventBus.class);
                    assertThat(context.getBean(EventBus.class)).isInstanceOf(KafkaEventBus.class);
                });
    }

    @Test
    @DisplayName("Kafka event bus is not used when kafka.enabled is not set, even with a KafkaTemplate present")
    void kafkaEventBusNotSelectedWhenDisabled() {
        contextRunner
                .withPropertyValues("adhar.event-sourcing.event-store-type=in-memory")
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .run(context -> assertThat(context.getBean(EventBus.class)).isInstanceOf(SimpleEventBus.class));
    }

    @Test
    @DisplayName("adhar.event-sourcing.enabled=false skips auto-configuration entirely")
    void disabledPropertySkipsAutoConfiguration() {
        contextRunner
                .withPropertyValues("adhar.event-sourcing.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EventBus.class));
    }

    @Test
    @DisplayName("retryMaxAttempts and snapshotInterval properties flow through to the wired beans")
    void propertiesAreHonored() {
        contextRunner
                .withPropertyValues(
                        "adhar.event-sourcing.event-store-type=in-memory",
                        "adhar.event-sourcing.snapshot-interval=5",
                        "adhar.event-sourcing.retry-max-attempts=7"
                )
                .run(context -> {
                    EventSourcingProperties properties = context.getBean(EventSourcingProperties.class);
                    assertThat(properties.getSnapshotInterval()).isEqualTo(5);
                    assertThat(properties.getRetryMaxAttempts()).isEqualTo(7);
                });
    }
}
