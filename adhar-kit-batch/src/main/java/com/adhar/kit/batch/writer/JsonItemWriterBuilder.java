package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.infrastructure.item.json.JsonFileItemWriter;
import org.springframework.batch.infrastructure.item.json.JsonObjectMarshaller;
import org.springframework.batch.infrastructure.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.WritableResource;

/**
 * Utility for creating JSON {@link JsonFileItemWriter} instances with sensible
 * defaults, in the same style as {@link CsvItemWriterBuilder}.
 *
 * <p>By default a Jackson-based {@link JsonObjectMarshaller} serializes each item
 * and the writer emits a well-formed top-level JSON array. A custom marshaller
 * (e.g. Gson) can be supplied for full control.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var writer = JsonItemWriterBuilder.<Order>jsonWriter("/output/orders.json")
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class JsonItemWriterBuilder {

    private JsonItemWriterBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link JsonFileItemWriterBuilder} for the given
     * file path, using a Jackson object marshaller.
     *
     * @param filePath the path to the output JSON file
     * @param <T>      the item type to write
     * @return a pre-configured builder ready for further customization
     */
    public static <T> JsonFileItemWriterBuilder<T> jsonWriter(String filePath) {
        return jsonWriter(new FileSystemResource(filePath), new JacksonJsonObjectMarshaller<>());
    }

    /**
     * Creates a pre-configured builder for the given resource and marshaller.
     *
     * @param resource   the writable JSON resource
     * @param marshaller the object marshaller used to serialize each item
     * @param <T>        the item type to write
     * @return a pre-configured builder ready for further customization
     */
    public static <T> JsonFileItemWriterBuilder<T> jsonWriter(WritableResource resource, JsonObjectMarshaller<T> marshaller) {
        return new JsonFileItemWriterBuilder<T>()
                .name(deriveWriterName(resource))
                .resource(resource)
                .jsonObjectMarshaller(marshaller);
    }

    private static String deriveWriterName(WritableResource resource) {
        var fileName = resource.getFilename();
        if (fileName == null || fileName.isBlank()) {
            return "JsonWriter";
        }
        var baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return baseName + "JsonWriter";
    }
}
