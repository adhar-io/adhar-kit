package com.adhar.kit.batch.writer;

import com.adhar.kit.batch.reader.JsonLinesItemReaderBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JsonLinesItemWriterBuilder}: one value per line with {@code \n}, and never a value split
 * over lines.
 */
class JsonLinesItemWriterBuilderTest {

    /** A line. */
    public record Line(long id, String name) {
    }

    @TempDir
    Path dir;

    @Test
    @DisplayName("writes one compact value per line, UTF-8, LF only, and reads back identically")
    void roundTrip() throws Exception {
        Path path = dir.resolve("out.jsonl");
        var writer = JsonLinesItemWriterBuilder.writer(new FileSystemResource(path), Line.class).build();
        writer.open(new ExecutionContext());
        writer.write(Chunk.of(new Line(1, "Zürich"), new Line(2, "a\nb\r\nc")));
        writer.close();

        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("{\"id\":1,\"name\":\"Zürich\"}\n{\"id\":2,\"name\":\"a\\nb\\r\\nc\"}\n");

        var reader = JsonLinesItemReaderBuilder.reader(new FileSystemResource(path), Line.class).build();
        reader.open(new ExecutionContext());
        assertThat(List.of(reader.read(), reader.read())).containsExactly(new Line(1, "Zürich"), new Line(2, "a\nb\r\nc"));
        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    @DisplayName("a mapper that indents is refused rather than splitting an item over lines")
    void indentingMapperRefused() {
        JsonMapper indenting = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

        assertThatThrownBy(() -> JsonLinesItemWriterBuilder.lineAggregator(indenting).aggregate(Map.of("a", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one line");
        assertThat(JsonLinesItemWriterBuilder.lineAggregator().aggregate(Map.of("a", 1))).isEqualTo("{\"a\":1}");
    }
}
