package com.adhar.kit.commons.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AdharCloudEventTest {

    @Test
    @DisplayName("of(source, type, data) creates event with a valid UUID id")
    void ofCreatesEventWithUuidId() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("test-source", "test.type", "payload");

        assertThat(event.id()).isNotNull().isNotBlank();
        // Verify it is a valid UUID
        assertThatNoException().isThrownBy(() -> UUID.fromString(event.id()));
    }

    @Test
    @DisplayName("of(source, type, data) populates source, type, and data correctly")
    void ofPopulatesFields() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("my-source", "my.type", "my-data");

        assertThat(event.source()).isEqualTo("my-source");
        assertThat(event.type()).isEqualTo("my.type");
        assertThat(event.data()).isEqualTo("my-data");
        assertThat(event.subject()).isNull();
    }

    @Test
    @DisplayName("of(source, type, subject, data) includes subject")
    void ofWithSubjectIncludesSubject() {
        AdharCloudEvent<String> event = AdharCloudEvent.of(
                "src", "type", "order-123", "data"
        );

        assertThat(event.subject()).isEqualTo("order-123");
        assertThat(event.source()).isEqualTo("src");
        assertThat(event.type()).isEqualTo("type");
        assertThat(event.data()).isEqualTo("data");
    }

    @Test
    @DisplayName("specVersion is always '1.0'")
    void specVersionIsAlwaysOnePointZero() {
        AdharCloudEvent<String> event1 = AdharCloudEvent.of("s", "t", "d");
        AdharCloudEvent<Integer> event2 = AdharCloudEvent.of("s", "t", "subj", 42);

        assertThat(event1.specVersion()).isEqualTo("1.0");
        assertThat(event2.specVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("time is set automatically and is recent")
    void timeIsSetAutomatically() {
        Instant before = Instant.now();
        AdharCloudEvent<String> event = AdharCloudEvent.of("s", "t", "d");
        Instant after = Instant.now();

        assertThat(event.time()).isNotNull();
        assertThat(event.time()).isBetween(before, after);
    }

    @Test
    @DisplayName("dataContentType defaults to 'application/json'")
    void dataContentTypeDefaultsToApplicationJson() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("s", "t", "d");

        assertThat(event.dataContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("dataContentType defaults to 'application/json' for subject variant")
    void dataContentTypeDefaultsForSubjectVariant() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("s", "t", "subj", "d");

        assertThat(event.dataContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("id is unique across multiple calls")
    void idIsUniqueAcrossMultipleCalls() {
        Set<String> ids = new HashSet<>();
        int count = 100;

        for (int i = 0; i < count; i++) {
            AdharCloudEvent<Integer> event = AdharCloudEvent.of("s", "t", i);
            ids.add(event.id());
        }

        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("null data is handled gracefully")
    void nullDataIsHandledGracefully() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("s", "t", (String) null);

        assertThat(event.data()).isNull();
        assertThat(event.id()).isNotNull();
        assertThat(event.specVersion()).isEqualTo("1.0");
        assertThat(event.time()).isNotNull();
    }

    @Test
    @DisplayName("null data with subject variant is handled gracefully")
    void nullDataWithSubjectIsHandledGracefully() {
        AdharCloudEvent<Object> event = AdharCloudEvent.of("s", "t", "subj", null);

        assertThat(event.data()).isNull();
        assertThat(event.subject()).isEqualTo("subj");
    }

    @Test
    @DisplayName("record is immutable - accessors return consistent values")
    void recordImmutability() {
        AdharCloudEvent<String> event = AdharCloudEvent.of("source", "type", "subject", "data");

        // Access multiple times to verify consistency
        String id1 = event.id();
        String id2 = event.id();
        Instant time1 = event.time();
        Instant time2 = event.time();

        assertThat(id1).isEqualTo(id2);
        assertThat(time1).isEqualTo(time2);
        assertThat(event.source()).isEqualTo("source");
        assertThat(event.type()).isEqualTo("type");
        assertThat(event.subject()).isEqualTo("subject");
        assertThat(event.data()).isEqualTo("data");
    }

    @Test
    @DisplayName("record equality works based on field values")
    void recordEqualityWorks() {
        Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
        AdharCloudEvent<String> event1 = new AdharCloudEvent<>(
                "id-1", "src", "type", "1.0", fixedTime, null, "application/json", "data"
        );
        AdharCloudEvent<String> event2 = new AdharCloudEvent<>(
                "id-1", "src", "type", "1.0", fixedTime, null, "application/json", "data"
        );

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("works with complex data types")
    void worksWithComplexDataTypes() {
        record OrderData(String orderId, double amount) {}
        OrderData order = new OrderData("ORD-001", 99.99);

        AdharCloudEvent<OrderData> event = AdharCloudEvent.of(
                "order-service", "order.created", "ORD-001", order
        );

        assertThat(event.data()).isEqualTo(order);
        assertThat(event.data().orderId()).isEqualTo("ORD-001");
        assertThat(event.data().amount()).isEqualTo(99.99);
    }
}
