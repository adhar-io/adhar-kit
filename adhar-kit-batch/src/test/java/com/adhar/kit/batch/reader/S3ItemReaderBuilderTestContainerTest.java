package com.adhar.kit.batch.reader;

import com.adhar.kit.batch.support.RustFsStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link S3ItemReaderBuilder} against RustFS: one object, a prefix in key order, restart inside an
 * object, the changed-input guard, and listings past one page.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3ItemReaderBuilderTestContainerTest {

    /** A line. */
    public record Line(long id) {
    }

    private S3Client s3;
    private String bucket;

    @BeforeEach
    void setUp() {
        s3 = RustFsStorage.client();
        bucket = RustFsStorage.createBucket("reader");
    }

    @AfterEach
    void tearDown() {
        s3.close();
    }

    private void put(String key, String body) {
        s3.putObject(request -> request.bucket(bucket).key(key), RequestBody.fromString(body));
    }

    private static String lines(int from, int to) {
        StringBuilder body = new StringBuilder();
        for (int id = from; id <= to; id++) {
            body.append("{\"id\":").append(id).append("}\n");
        }
        return body.toString();
    }

    private static List<Long> read(S3ObjectsItemReader<Line> reader, ExecutionContext context, int max) throws Exception {
        List<Long> ids = new ArrayList<>();
        reader.open(context);
        Line line;
        while (ids.size() < max && (line = reader.read()) != null) {
            ids.add(line.id());
        }
        reader.update(context);
        reader.close();
        return ids;
    }

    private S3ItemReaderBuilder<Line> day() {
        return S3ItemReaderBuilder.<Line>of(s3, bucket).prefix("day=2026-11-03/").suffix(".jsonl").jsonLines(Line.class);
    }

    @Test
    @DisplayName("one object by key")
    void oneObject() throws Exception {
        put("inbound/records.jsonl", lines(1, 3));

        var reader = S3ItemReaderBuilder.<Line>of(s3, bucket).key("inbound/records.jsonl").jsonLines(Line.class).build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("every object under a prefix, in key order, skipping markers and other days")
    void prefixInKeyOrder() throws Exception {
        put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        put("day=2026-11-03/_SUCCESS", "");
        put("day=2026-11-04/part-00000.jsonl", lines(100, 101));

        S3ObjectsItemReader<Line> reader = day().build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(reader.keys()).containsExactly("day=2026-11-03/part-00000.jsonl", "day=2026-11-03/part-00001.jsonl");
    }

    @Test
    @DisplayName("a restart resumes inside the object it stopped in, at the next line")
    void restartInsideAnObject() throws Exception {
        put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        ExecutionContext context = new ExecutionContext();

        List<Long> first = read(day().build(), context, 4);
        List<Long> rest = read(day().build(), context, 99);

        assertThat(first).containsExactly(1L, 2L, 3L, 4L);
        assertThat(rest).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("a restart over input that changed since the failed attempt is refused")
    void changedInputRefused() throws Exception {
        put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        ExecutionContext context = new ExecutionContext();
        read(day().build(), context, 4);

        put("day=2026-11-03/part-00000.jsonl", lines(1, 2));

        assertThatThrownBy(() -> day().build().open(context)).isInstanceOf(ItemStreamException.class)
                .hasMessageContaining("changed since the failed attempt");
        ExecutionContext fresh = new ExecutionContext();
        assertThat(read(day().build(), fresh, 99)).containsExactly(1L, 2L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("an empty selection fails the step unless the reader is lenient")
    void emptySelection() throws Exception {
        assertThatThrownBy(() -> day().build().open(new ExecutionContext())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> S3ItemReaderBuilder.<Line>of(s3, bucket).key("missing.jsonl").jsonLines(Line.class)
                .build().open(new ExecutionContext())).isInstanceOf(IllegalStateException.class);

        assertThat(read(day().strict(false).build(), new ExecutionContext(), 99)).isEmpty();
    }

    @Test
    @DisplayName("CSV objects with a header line each, through a delimited line mapper")
    void csvWithHeaders() throws Exception {
        put("csv/a.csv", "id\n1\n2\n");
        put("csv/b.csv", "id\n3\n");
        DefaultLineMapper<Line> mapper = new DefaultLineMapper<>();
        mapper.setLineTokenizer(new DelimitedLineTokenizer());
        mapper.setFieldSetMapper(fields -> new Line(fields.readLong(0)));

        var reader = S3ItemReaderBuilder.<Line>of(s3, bucket).prefix("csv/").lines(mapper, 1).build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("a prefix of more than one listing page is read completely")
    void moreThanOnePage() throws Exception {
        IntStream.range(0, 1_003).parallel().forEach(i -> put("many/part-%05d.jsonl".formatted(i), lines(i, i)));

        var reader = S3ItemReaderBuilder.<Line>of(s3, bucket).prefix("many/").jsonLines(Line.class).build();

        List<Long> ids = read(reader, new ExecutionContext(), Integer.MAX_VALUE);
        assertThat(ids).hasSize(1_003).isSorted();
        assertThat(S3ItemReaderBuilder.childPrefixes(s3, bucket, "")).containsExactly("many/");
    }
}
