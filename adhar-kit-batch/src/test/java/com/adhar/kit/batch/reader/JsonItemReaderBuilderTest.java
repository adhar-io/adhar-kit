package com.adhar.kit.batch.reader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.json.JacksonJsonObjectReader;
import org.springframework.batch.infrastructure.item.json.JsonItemReader;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonItemReaderBuilder}.
 */
class JsonItemReaderBuilderTest {

    /** Simple bean used as the mapping target. */
    public record Order(long id, String customerName) {
    }

    @Test
    @DisplayName("jsonReader builds a reader with a Jackson object reader")
    void jsonReaderFromPath() {
        JsonItemReader<Order> reader = JsonItemReaderBuilder
                .jsonReader("/data/orders.json", Order.class)
                .build();

        assertThat(reader).isNotNull();
    }

    @Test
    @DisplayName("jsonReader accepts a custom resource and object reader")
    void jsonReaderCustomObjectReader() {
        var resource = new ByteArrayResource("[]".getBytes());
        var reader = JsonItemReaderBuilder
                .jsonReader(resource, Order.class, new JacksonJsonObjectReader<>(Order.class))
                .build();

        assertThat(reader).isNotNull();
    }
}
