package com.adhar.kit.dapr.serialization;

import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DaprJsonSerializerTest {

    record Shipment(String id, Instant shippedAt, String carrier) {
    }

    private final DaprJsonSerializer serializer = new DaprJsonSerializer();

    @Test
    void javaTimeIsWrittenAsIsoTextAndReadBack() {
        byte[] json = serializer.toBytes(new Shipment("s-1", Instant.parse("2026-10-28T01:02:03Z"), null));

        assertThat(new String(json, StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":\"s-1\",\"shippedAt\":\"2026-10-28T01:02:03Z\"}");
        assertThat(serializer.fromBytes(json, TypeRef.get(Shipment.class)).shippedAt())
                .isEqualTo(Instant.parse("2026-10-28T01:02:03Z"));
    }

    @Test
    void bytesPassThroughAndGenericsAreHonoured() {
        assertThat(serializer.toBytes(new byte[] {1, 2})).containsExactly(1, 2);
        List<Shipment> shipments = serializer.fromBytes(
                "[{\"id\":\"a\",\"unknown\":true}]".getBytes(StandardCharsets.UTF_8), new TypeRef<List<Shipment>>() { });
        assertThat(shipments.getFirst()).isInstanceOf(Shipment.class);
        assertThat((Shipment) serializer.convert(Map.of("id", "b"), Shipment.class)).isEqualTo(new Shipment("b", null, null));
        assertThat(serializer.getContentType()).isEqualTo("application/json");
    }
}
