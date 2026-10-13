package com.adhar.kit.eventsourcing.config;

import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which event store a service gets. JPA is on the classpath here, as it is for every
 * adhar-kit-starter service; that alone must not select the JPA store.
 */
class EventStoreSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventSourcingAutoConfiguration.class));

    @Test
    void defaultsToInMemory() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed()
                .getBean(EventStore.class).isInstanceOf(InMemoryEventStore.class));
    }

    @Test
    void jpaWithoutAnEntityManagerFactoryFallsBackToInMemory() {
        // Used to leave no EventStore at all, failing startup on aggregateRepository.
        runner.withPropertyValues("adhar.event-sourcing.event-store-type=jpa")
                .run(ctx -> assertThat(ctx).hasNotFailed()
                        .getBean(EventStore.class).isInstanceOf(InMemoryEventStore.class));
    }

    @Test
    void explicitInMemory() {
        runner.withPropertyValues("adhar.event-sourcing.event-store-type=in-memory")
                .run(ctx -> assertThat(ctx.getBean(EventStore.class)).isInstanceOf(InMemoryEventStore.class));
    }
}
