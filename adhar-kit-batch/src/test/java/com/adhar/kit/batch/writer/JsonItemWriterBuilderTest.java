package com.adhar.kit.batch.writer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.infrastructure.item.json.JsonFileItemWriter;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonItemWriterBuilder}.
 */
class JsonItemWriterBuilderTest {

    /** Simple bean used as the item type. */
    public record Order(long id, String customerName) {
    }

    @Test
    @DisplayName("jsonWriter builds a writer from a file path")
    void jsonWriterFromPath() {
        JsonFileItemWriter<Order> writer = JsonItemWriterBuilder
                .<Order>jsonWriter("/output/orders.json")
                .build();

        assertThat(writer).isNotNull();
    }

    @Test
    @DisplayName("jsonWriter accepts a custom resource and marshaller")
    void jsonWriterCustomMarshaller() {
        var resource = new FileSystemResource("/output/custom.json");
        var writer = JsonItemWriterBuilder
                .jsonWriter(resource, new JacksonJsonObjectMarshaller<Order>())
                .build();

        assertThat(writer).isNotNull();
    }

    @Test
    @DisplayName("writer name falls back gracefully for a null filename")
    void writerNameFallback() {
        var writer = JsonItemWriterBuilder
                .jsonWriter(new NullWritableResource(), new JacksonJsonObjectMarshaller<Order>())
                .build();

        assertThat(writer).isNotNull();
    }

    /** Minimal writable resource with a null filename. */
    private static final class NullWritableResource extends org.springframework.core.io.AbstractResource
            implements org.springframework.core.io.WritableResource {
        @Override
        public String getDescription() {
            return "null-filename";
        }

        @Override
        public String getFilename() {
            return null;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
    }
}
