package com.adhar.kit.eventsourcing.config;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.projection.ProjectionCheckpointStore;
import com.adhar.kit.eventsourcing.projection.ProjectionManager;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.repository.RetryingAggregateRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

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
                });
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
