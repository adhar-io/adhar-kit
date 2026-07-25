package com.adhar.kit.eventsourcing.upcast;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UpcasterChain")
class UpcasterChainTest {

    private DomainEvent event(String eventType, int version, String payload) {
        return new DomainEvent("evt-1", "order-1", "OrderAggregate", version, eventType, payload,
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    /** Upcasts "OrderCreatedV1" (payload {"amount":N}) to "OrderCreatedV2" (payload {"amountCents":N*100}). */
    private static class V1ToV2Upcaster implements EventUpcaster {
        @Override
        public boolean supports(String eventType, int version) {
            return "OrderCreatedV1".equals(eventType);
        }

        @Override
        public DomainEvent upcast(DomainEvent event) {
            int amount = Integer.parseInt(event.payload().replaceAll("[^0-9]", ""));
            return new DomainEvent(event.eventId(), event.aggregateId(), event.aggregateType(), event.version(),
                    "OrderCreatedV2", "{\"amountCents\":" + (amount * 100) + "}", event.occurredAt());
        }
    }

    /** Upcasts "OrderCreatedV2" to "OrderCreatedV3" by renaming the field. */
    private static class V2ToV3Upcaster implements EventUpcaster {
        @Override
        public boolean supports(String eventType, int version) {
            return "OrderCreatedV2".equals(eventType);
        }

        @Override
        public DomainEvent upcast(DomainEvent event) {
            String cents = event.payload().replaceAll("[^0-9]", "");
            return new DomainEvent(event.eventId(), event.aggregateId(), event.aggregateType(), event.version(),
                    "OrderCreatedV3", "{\"totalCents\":" + cents + "}", event.occurredAt());
        }
    }

    @Test
    @DisplayName("empty chain returns the event unchanged")
    void emptyChainReturnsUnchanged() {
        DomainEvent original = event("OrderCreatedV1", 1, "{\"amount\":10}");

        DomainEvent result = UpcasterChain.empty().apply(original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("a single matching upcaster migrates the event")
    void singleUpcasterMigrates() {
        UpcasterChain chain = new UpcasterChain(List.of(new V1ToV2Upcaster()));

        DomainEvent result = chain.apply(event("OrderCreatedV1", 1, "{\"amount\":10}"));

        assertThat(result.eventType()).isEqualTo("OrderCreatedV2");
        assertThat(result.payload()).isEqualTo("{\"amountCents\":1000}");
    }

    @Test
    @DisplayName("chained upcasters registered in order fully migrate a multi-step event in one apply() call")
    void chainedUpcastersApplyInOrder() {
        UpcasterChain chain = new UpcasterChain(List.of(new V1ToV2Upcaster(), new V2ToV3Upcaster()));

        DomainEvent result = chain.apply(event("OrderCreatedV1", 1, "{\"amount\":10}"));

        assertThat(result.eventType()).isEqualTo("OrderCreatedV3");
        assertThat(result.payload()).isEqualTo("{\"totalCents\":1000}");
    }

    @Test
    @DisplayName("upcasters are still applied when registered out of dependency order (chain repeats passes)")
    void upcastersApplyRegardlessOfRegistrationOrder() {
        UpcasterChain chain = new UpcasterChain(List.of(new V2ToV3Upcaster(), new V1ToV2Upcaster()));

        DomainEvent result = chain.apply(event("OrderCreatedV1", 1, "{\"amount\":10}"));

        assertThat(result.eventType()).isEqualTo("OrderCreatedV3");
        assertThat(result.payload()).isEqualTo("{\"totalCents\":1000}");
    }

    @Test
    @DisplayName("an event that no upcaster supports is returned unchanged")
    void unsupportedEventTypeUnchanged() {
        UpcasterChain chain = new UpcasterChain(List.of(new V1ToV2Upcaster()));
        DomainEvent original = event("SomethingElse", 1, "{}");

        DomainEvent result = chain.apply(original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("applyAll migrates every event in a list, preserving order")
    void applyAllMigratesEveryEvent() {
        UpcasterChain chain = new UpcasterChain(List.of(new V1ToV2Upcaster()));
        List<DomainEvent> events = List.of(
                event("OrderCreatedV1", 1, "{\"amount\":10}"),
                event("OrderCreatedV1", 2, "{\"amount\":20}")
        );

        List<DomainEvent> results = chain.applyAll(events);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).payload()).isEqualTo("{\"amountCents\":1000}");
        assertThat(results.get(1).payload()).isEqualTo("{\"amountCents\":2000}");
    }
}
