package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.LineAggregator;
import org.springframework.core.io.WritableResource;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Writers of JSON Lines (one JSON value per line, UTF-8, {@code \n}), until Spring Batch 6.1's
 * {@code JsonLineAggregator} is GA. The writer is a transactional {@code FlatFileItemWriter}, so a
 * restart truncates the file to the last committed chunk.
 *
 * <pre>{@code
 * FlatFileItemWriter<Record> writer = JsonLinesItemWriterBuilder.writer(resource, Record.class).build();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class JsonLinesItemWriterBuilder {

    private JsonLinesItemWriterBuilder() {
        // utility class
    }

    /**
     * @param resource the file to write
     * @param type     the item type (names the writer)
     * @param <T>      the item type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemWriterBuilder<T> writer(WritableResource resource, Class<T> type) {
        return writer(resource, type, JsonMapper.shared());
    }

    /**
     * @param resource the file to write
     * @param type     the item type (names the writer)
     * @param mapper   the mapper
     * @param <T>      the item type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemWriterBuilder<T> writer(WritableResource resource, Class<T> type, JsonMapper mapper) {
        return new FlatFileItemWriterBuilder<T>()
                .name(type.getSimpleName() + "JsonLinesWriter")
                .resource(resource)
                .encoding(StandardCharsets.UTF_8.name())
                // The platform default would be \r\n on Windows; JSON Lines is \n everywhere.
                .lineSeparator("\n")
                .lineAggregator(lineAggregator(mapper));
    }

    /**
     * @param <T> the item type
     * @return a line aggregator for any flat-file or object writer
     */
    public static <T> LineAggregator<T> lineAggregator() {
        return lineAggregator(JsonMapper.shared());
    }

    /**
     * A mapper configured to indent would split one item over several lines, and every reader of
     * the file would then fail on a fragment: such a line is refused here instead.
     *
     * @param mapper the mapper
     * @param <T>    the item type
     * @return a line aggregator for any flat-file or object writer
     */
    public static <T> LineAggregator<T> lineAggregator(JsonMapper mapper) {
        ObjectWriter writer = mapper.writer();
        return item -> {
            String line = writer.writeValueAsString(item);
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalStateException("a JSON Lines item must serialise to one line; the mapper writes "
                        + "line breaks (is INDENT_OUTPUT enabled?)");
            }
            return line;
        };
    }
}
