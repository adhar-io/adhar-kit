package com.adhar.kit.batch.reader;

import org.springframework.batch.infrastructure.item.json.JacksonJsonObjectReader;
import org.springframework.batch.infrastructure.item.json.JsonItemReader;
import org.springframework.batch.infrastructure.item.json.JsonObjectReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Utility for creating JSON {@link JsonItemReader} instances with sensible
 * defaults, in the same style as {@link CsvItemReaderBuilder} and
 * {@link JpaItemReaderBuilder}.
 *
 * <p>By default a Jackson-based {@link JsonObjectReader} is used to deserialize a
 * top-level JSON array into items of the target type. A custom object reader
 * (e.g. Gson) can be supplied for full control.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var reader = JsonItemReaderBuilder.jsonReader("/data/orders.json", Order.class)
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class JsonItemReaderBuilder {

    private JsonItemReaderBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured Spring Batch
     * {@link org.springframework.batch.infrastructure.item.json.builder.JsonItemReaderBuilder}
     * for the given file path, using a Jackson object reader.
     *
     * @param filePath   the path to the JSON file (a top-level array)
     * @param targetType the type to deserialize each element to
     * @param <T>        the target type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> org.springframework.batch.infrastructure.item.json.builder.JsonItemReaderBuilder<T> jsonReader(
            String filePath, Class<T> targetType) {
        return jsonReader(new FileSystemResource(filePath), targetType, new JacksonJsonObjectReader<>(targetType));
    }

    /**
     * Creates a pre-configured builder for the given resource and object reader.
     *
     * @param resource     the JSON resource (a top-level array)
     * @param targetType   the type to deserialize each element to
     * @param objectReader the object reader used to parse each element
     * @param <T>          the target type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> org.springframework.batch.infrastructure.item.json.builder.JsonItemReaderBuilder<T> jsonReader(
            Resource resource, Class<T> targetType, JsonObjectReader<T> objectReader) {
        return new org.springframework.batch.infrastructure.item.json.builder.JsonItemReaderBuilder<T>()
                .name(targetType.getSimpleName() + "JsonReader")
                .resource(resource)
                .jsonObjectReader(objectReader);
    }
}
