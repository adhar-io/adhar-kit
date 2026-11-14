package com.adhar.kit.batch.reader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JsonLinesItemReaderBuilder}: one value per line, UTF-8, restartable by line.
 */
class JsonLinesItemReaderBuilderTest {

    /** A line. */
    public record Line(long id, String name) {
    }

    @TempDir
    Path dir;

    private FileSystemResource file(String content) throws Exception {
        Path path = dir.resolve("in.jsonl");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileSystemResource(path);
    }

    private static List<Line> read(FlatFileItemReader<Line> reader, ExecutionContext context, int max) throws Exception {
        List<Line> lines = new ArrayList<>();
        reader.open(context);
        Line line;
        while (lines.size() < max && (line = reader.read()) != null) {
            lines.add(line);
        }
        reader.update(context);
        reader.close();
        return lines;
    }

    @Test
    @DisplayName("reads one value per line, as UTF-8, including escaped line breaks inside strings")
    void readsLines() throws Exception {
        var resource = file("{\"id\":1,\"name\":\"Zürich\"}\n{\"id\":2,\"name\":\"a\\nb\"}\n{\"id\":3,\"name\":\"\\u2028\"}\n");

        assertThat(read(JsonLinesItemReaderBuilder.reader(resource, Line.class).build(), new ExecutionContext(), 99))
                .containsExactly(new Line(1, "Zürich"), new Line(2, "a\nb"), new Line(3, "\u2028"));
    }

    @Test
    @DisplayName("a restart continues at the next line")
    void restartContinues() throws Exception {
        var resource = file("{\"id\":1}\n{\"id\":2}\n{\"id\":3}\n{\"id\":4}\n");
        ExecutionContext context = new ExecutionContext();

        List<Line> first = read(JsonLinesItemReaderBuilder.reader(resource, Line.class).build(), context, 2);
        List<Line> rest = read(JsonLinesItemReaderBuilder.reader(resource, Line.class).build(), context, 99);

        assertThat(first).extracting(Line::id).containsExactly(1L, 2L);
        assertThat(rest).extracting(Line::id).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("a malformed or blank line fails with its line number")
    void malformedLineFails() throws Exception {
        var reader = JsonLinesItemReaderBuilder.reader(file("{\"id\":1}\n{\"id\":\n"), Line.class).build();
        reader.open(new ExecutionContext());
        reader.read();

        assertThatThrownBy(reader::read).isInstanceOf(FlatFileParseException.class)
                .satisfies(ex -> assertThat(((FlatFileParseException) ex).getLineNumber()).isEqualTo(2));

        var blank = JsonLinesItemReaderBuilder.reader(file("{\"id\":1}\n\n{\"id\":2}\n"), Line.class).build();
        blank.open(new ExecutionContext());
        blank.read();
        assertThatThrownBy(blank::read).isInstanceOf(FlatFileParseException.class);
    }
}
