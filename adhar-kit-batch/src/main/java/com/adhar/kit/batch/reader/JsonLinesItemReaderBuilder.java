package com.adhar.kit.batch.reader;

import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Readers of JSON Lines (one JSON value per line, UTF-8), until Spring Batch 6.1's
 * {@code JsonLineMapper} is GA. The reader is a {@code FlatFileItemReader}, so it restarts by line
 * count like any flat file; a blank or malformed line fails with its line number.
 *
 * <pre>{@code
 * FlatFileItemReader<InputRecord> reader = JsonLinesItemReaderBuilder.reader(resource, InputRecord.class).build();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class JsonLinesItemReaderBuilder {

    private JsonLinesItemReaderBuilder() {
        // utility class
    }

    /**
     * @param resource the JSON Lines file
     * @param type     the type each line is read as
     * @param <T>      the item type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemReaderBuilder<T> reader(Resource resource, Class<T> type) {
        return reader(resource, type, JsonMapper.shared());
    }

    /**
     * @param resource the JSON Lines file
     * @param type     the type each line is read as
     * @param mapper   the mapper
     * @param <T>      the item type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemReaderBuilder<T> reader(Resource resource, Class<T> type, JsonMapper mapper) {
        return new FlatFileItemReaderBuilder<T>()
                .name(type.getSimpleName() + "JsonLinesReader")
                .resource(resource)
                .encoding(StandardCharsets.UTF_8.name())
                .lineMapper(lineMapper(type, mapper));
    }

    /**
     * @param type the type each line is read as
     * @param <T>  the item type
     * @return a line mapper for any flat-file reader, e.g. the delegate of an S3 prefix reader
     */
    public static <T> LineMapper<T> lineMapper(Class<T> type) {
        return lineMapper(type, JsonMapper.shared());
    }

    /**
     * @param type   the type each line is read as
     * @param mapper the mapper
     * @param <T>    the item type
     * @return a line mapper for any flat-file reader
     */
    public static <T> LineMapper<T> lineMapper(Class<T> type, JsonMapper mapper) {
        ObjectReader reader = mapper.readerFor(type);
        return (line, lineNumber) -> reader.readValue(line);
    }
}
