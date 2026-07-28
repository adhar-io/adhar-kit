package com.adhar.kit.eventsourcing.saga;

import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SagaManager")
class SagaManagerTest {

    private InMemorySagaStateStore store;
    private List<String> executed;
    private List<String> compensated;

    @BeforeEach
    void setUp() {
        store = new InMemorySagaStateStore();
        executed = new ArrayList<>();
        compensated = new ArrayList<>();
    }

    private SagaStep step(String name) {
        return SagaStep.of(name, ctx -> executed.add(name), ctx -> compensated.add(name));
    }

    private SagaStep failingStep(String name) {
        return SagaStep.of(name,
                ctx -> { throw new RuntimeException("failure in " + name); },
                ctx -> compensated.add(name));
    }

    private DomainEvent event(String type, String aggregateId) {
        return new DomainEvent("evt", aggregateId, "OrderAggregate", 1, type, "{}", Instant.now());
    }

    @Test
    @DisplayName("a synchronous saga executes all steps in order and completes")
    void executesAllStepsAndCompletes() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(step("reserve"))
                .step(step("charge"))
                .step(step("ship"))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());

        assertThat(executed).containsExactly("reserve", "charge", "ship");
        assertThat(compensated).isEmpty();
        SagaInstance stored = store.findById(instance.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(stored.getCurrentStepIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("a failing step compensates completed steps in reverse order")
    void compensatesCompletedStepsInReverse() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(step("reserve"))
                .step(step("charge"))
                .step(failingStep("ship"))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());

        assertThat(executed).containsExactly("reserve", "charge");
        assertThat(compensated).containsExactly("charge", "reserve");
        assertThat(store.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    @DisplayName("a failing compensation marks the saga FAILED")
    void failingCompensationMarksFailed() {
        SagaManager manager = new SagaManager(store);
        SagaStep reserve = SagaStep.of("reserve",
                ctx -> executed.add("reserve"),
                ctx -> { throw new RuntimeException("compensation blew up"); });
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(reserve)
                .step(failingStep("charge"))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());

        assertThat(store.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(SagaStatus.FAILED);
    }

    @Test
    @DisplayName("step actions can read and write shared context data that is persisted")
    void stepsShareAndPersistContextData() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(SagaStep.of("compute", ctx -> ctx.put("total", 100), ctx -> { }))
                .step(SagaStep.of("verify", ctx -> {
                    assertThat(ctx.get("total")).isEqualTo(100);
                    ctx.put("verified", true);
                }, ctx -> { }))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of("customer", "alice"));

        SagaInstance stored = store.findById(instance.getId()).orElseThrow();
        assertThat(stored.getData()).containsEntry("total", 100)
                .containsEntry("verified", true)
                .containsEntry("customer", "alice");
    }

    @Test
    @DisplayName("an asynchronous step pauses awaiting an event, then advances when it arrives")
    void asynchronousStepAwaitsAndResumes() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(step("reserve"))
                .step(SagaStep.builder("awaitPayment")
                        .action(ctx -> executed.add("awaitPayment"))
                        .compensation(ctx -> compensated.add("awaitPayment"))
                        .awaitEventType("PaymentConfirmed")
                        .failureEventType("PaymentFailed")
                        .build())
                .step(step("ship"))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());

        // Paused after running the async step's action.
        SagaInstance paused = store.findById(instance.getId()).orElseThrow();
        assertThat(paused.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(paused.getAwaitingEventType()).isEqualTo("PaymentConfirmed");
        assertThat(executed).containsExactly("reserve", "awaitPayment");

        manager.onEvent(event("PaymentConfirmed", "order-1"));

        assertThat(executed).containsExactly("reserve", "awaitPayment", "ship");
        assertThat(store.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    @DisplayName("an asynchronous step's failure event triggers compensation")
    void asynchronousFailureEventCompensates() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(step("reserve"))
                .step(SagaStep.builder("awaitPayment")
                        .action(ctx -> executed.add("awaitPayment"))
                        .compensation(ctx -> compensated.add("awaitPayment"))
                        .awaitEventType("PaymentConfirmed")
                        .failureEventType("PaymentFailed")
                        .build())
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());
        manager.onEvent(event("PaymentFailed", "order-1"));

        assertThat(compensated).containsExactly("reserve");
        assertThat(store.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    @DisplayName("progression events for a different correlation id are ignored")
    void ignoresUncorrelatedEvents() {
        SagaManager manager = new SagaManager(store);
        manager.register(SagaDefinition.builder("OrderSaga")
                .step(SagaStep.builder("awaitPayment")
                        .action(ctx -> executed.add("awaitPayment"))
                        .awaitEventType("PaymentConfirmed")
                        .build())
                .step(step("ship"))
                .build());

        SagaInstance instance = manager.start("OrderSaga", "order-1", Map.of());
        manager.onEvent(event("PaymentConfirmed", "order-999"));

        assertThat(executed).containsExactly("awaitPayment");
        assertThat(store.findById(instance.getId()).orElseThrow().getAwaitingEventType())
                .isEqualTo("PaymentConfirmed");
    }

    @Test
    @DisplayName("a definition with a start event type begins a new saga when that event is published")
    void eventDrivenStartViaBus() {
        SimpleEventBus bus = new SimpleEventBus();
        SagaManager manager = new SagaManager(store, bus);
        manager.register(SagaDefinition.builder("OrderSaga")
                .startEventType("OrderPlaced")
                .step(step("reserve"))
                .build());

        bus.publish(event("OrderPlaced", "order-1"));

        assertThat(executed).containsExactly("reserve");
        assertThat(store.findByStatus(SagaStatus.COMPLETED)).hasSize(1);
    }

    @Test
    @DisplayName("starting an unregistered saga throws")
    void startUnknownSagaThrows() {
        SagaManager manager = new SagaManager(store);
        assertThatThrownBy(() -> manager.start("Nope", "c1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("building a definition with no steps is rejected")
    void definitionRequiresSteps() {
        assertThatThrownBy(() -> SagaDefinition.builder("Empty").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
