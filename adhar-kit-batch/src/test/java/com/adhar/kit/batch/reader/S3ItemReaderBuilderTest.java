package com.adhar.kit.batch.reader;

import com.adhar.kit.batch.support.InMemoryS3Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link S3ItemReaderBuilder} and {@link S3ObjectsItemReader} without an object store: the
 * selection (key, prefix, suffix, markers), key order across listing pages, the restart position,
 * the changed-input guard and the builder's refusals, against {@link InMemoryS3Client}. The same
 * behaviour against RustFS is {@link S3ItemReaderBuilderTestContainerTest}.
 */
class S3ItemReaderBuilderTest {

    private static final String BUCKET = "inbound";

    /** A line. */
    public record Line(long id) {
    }

    private final InMemoryS3Client s3 = new InMemoryS3Client(BUCKET);

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
        return S3ItemReaderBuilder.<Line>of(s3, BUCKET).prefix("day=2026-11-03/").suffix(".jsonl").jsonLines(Line.class);
    }

    @Test
    @DisplayName("one object by key")
    void oneObject() throws Exception {
        s3.put("inbound/records.jsonl", lines(1, 3));
        s3.put("inbound/records.jsonl.bak", lines(7, 9));

        var reader = S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("inbound/records.jsonl").jsonLines(Line.class).build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L);
        assertThat(reader.keys()).containsExactly("inbound/records.jsonl");
        assertThat(s3.listCalls()).as("a key is looked up, not listed").isZero();
    }

    @Test
    @DisplayName("every object under a prefix, in key order, skipping markers, folders and other days")
    void prefixInKeyOrder() throws Exception {
        s3.put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        s3.put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        s3.put("day=2026-11-03/_SUCCESS", "");
        s3.put("day=2026-11-03/archive.jsonl/", "");
        s3.put("day=2026-11-04/part-00000.jsonl", lines(100, 101));

        S3ObjectsItemReader<Line> reader = day().build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(reader.keys()).containsExactly("day=2026-11-03/part-00000.jsonl", "day=2026-11-03/part-00001.jsonl");
    }

    @Test
    @DisplayName("a restart resumes inside the object it stopped in, at the next line")
    void restartInsideAnObject() throws Exception {
        s3.put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        s3.put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        ExecutionContext context = new ExecutionContext();

        List<Long> first = read(day().name("in").build(), context, 4);
        List<Long> rest = read(day().name("in").build(), context, 99);

        assertThat(first).containsExactly(1L, 2L, 3L, 4L);
        assertThat(rest).containsExactly(5L, 6L);
        assertThat(context.getString("in.objects.sha256")).hasSize(64);
    }

    @Test
    @DisplayName("a restart over input that changed since the failed attempt is refused: rewritten, added or removed")
    void changedInputRefused() throws Exception {
        s3.put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        s3.put("day=2026-11-03/part-00001.jsonl", lines(4, 6));
        ExecutionContext context = new ExecutionContext();
        read(day().build(), context, 4);

        s3.put("day=2026-11-03/part-00000.jsonl", lines(1, 2));
        assertThatThrownBy(() -> day().build().open(context)).isInstanceOf(ItemStreamException.class)
                .hasMessageContaining("s3://inbound").hasMessageContaining("changed since the failed attempt");

        s3.put("day=2026-11-03/part-00000.jsonl", lines(1, 3));
        assertThat(read(day().build(), new ExecutionContext(context), 99)).as("the input as it was").containsExactly(5L, 6L);
        s3.put("day=2026-11-03/part-00002.jsonl", lines(7, 7));
        assertThatThrownBy(() -> day().build().open(context)).isInstanceOf(ItemStreamException.class);

        ExecutionContext fresh = new ExecutionContext();
        assertThat(read(day().build(), fresh, 99)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    @DisplayName("a reader that saves no state records no listing and is not held to a recorded one")
    void withoutSavedStateThereIsNoGuard() throws Exception {
        s3.put("day=2026-11-03/part-00000.jsonl", lines(8, 9));
        ExecutionContext recorded = new ExecutionContext();
        recorded.putString("in.objects.sha256", "the-listing-of-another-attempt");
        assertThatThrownBy(() -> day().name("in").build().open(recorded)).isInstanceOf(ItemStreamException.class);

        S3ObjectsItemReader<Line> stateless = day().name("in").build();
        stateless.setSaveState(false);
        ExecutionContext untouched = new ExecutionContext();

        assertThat(read(stateless, recorded, 99)).containsExactly(8L, 9L);
        assertThat(recorded.getString("in.objects.sha256")).isEqualTo("the-listing-of-another-attempt");
        assertThat(read(stateless, untouched, 99)).containsExactly(8L, 9L);
        assertThat(untouched.containsKey("in.objects.sha256")).isFalse();
    }

    @Test
    @DisplayName("an empty selection fails the step unless the reader is lenient")
    void emptySelection() throws Exception {
        assertThatThrownBy(() -> day().build().open(new ExecutionContext())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("missing.jsonl").jsonLines(Line.class)
                .build().open(new ExecutionContext())).isInstanceOf(IllegalStateException.class);

        assertThat(read(day().strict(false).build(), new ExecutionContext(), 99)).isEmpty();
        assertThat(read(S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("missing.jsonl").jsonLines(Line.class).strict(false)
                .build(), new ExecutionContext(), 99)).isEmpty();
    }

    @Test
    @DisplayName("a key the store answers 404 for without an error code is missing; any other failure is the store's")
    void headFailures() {
        s3.put("inbound/records.jsonl", lines(1, 3));
        var reader = S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("inbound/records.jsonl").jsonLines(Line.class)
                .strict(false).build();

        s3.failHeadWith((S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build());
        reader.open(new ExecutionContext());
        assertThat(reader.keys()).isEmpty();
        reader.close();

        s3.failHeadWith((S3Exception) S3Exception.builder().statusCode(403).message("Forbidden")
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build()).build());
        assertThatThrownBy(() -> reader.open(new ExecutionContext())).isInstanceOf(S3Exception.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    @DisplayName("CSV objects with a header line each, through a delimited line mapper")
    void csvWithHeaders() throws Exception {
        s3.put("csv/a.csv", "id\n1\n2\n");
        s3.put("csv/b.csv", "id\n3\n");
        DefaultLineMapper<Line> mapper = new DefaultLineMapper<>();
        mapper.setLineTokenizer(new DelimitedLineTokenizer());
        mapper.setFieldSetMapper(fields -> new Line(fields.readLong(0)));

        var reader = S3ItemReaderBuilder.<Line>of(s3, BUCKET).prefix("csv/").lines(mapper, 1).build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("a delegate of the caller's own reads each object")
    void aDelegateOfTheCallersOwn() throws Exception {
        s3.put("raw/a.txt", "4\n5\n");
        FlatFileItemReader<Line> delegate = new FlatFileItemReader<>((line, number) -> new Line(Long.parseLong(line)));
        delegate.setName("raw");

        var reader = S3ItemReaderBuilder.<Line>of(s3, BUCKET).prefix("raw/").jsonLines(Line.class).delegate(delegate).build();

        assertThat(read(reader, new ExecutionContext(), 99)).containsExactly(4L, 5L);
    }

    @Test
    @DisplayName("a prefix of more than one listing page is read completely, in key order")
    void moreThanOnePage() throws Exception {
        s3.pageSize(4);
        for (int i = 10; i >= 0; i--) {
            s3.put("many/part-%05d.jsonl".formatted(i), lines(i, i));
        }

        var reader = S3ItemReaderBuilder.<Line>of(s3, BUCKET).prefix("many/").jsonLines(Line.class).build();

        List<Long> ids = read(reader, new ExecutionContext(), Integer.MAX_VALUE);
        assertThat(ids).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(s3.listCalls()).as("three pages of four").isEqualTo(3);
    }

    @Test
    @DisplayName("child prefixes are the directories directly below, sorted, across pages")
    void childPrefixes() {
        s3.pageSize(2);
        s3.put("day=2026-11-03/region=us/part-00000.jsonl", lines(1, 1));
        s3.put("day=2026-11-03/region=eu/part-00000.jsonl", lines(2, 2));
        s3.put("day=2026-11-03/region=eu/part-00001.jsonl", lines(3, 3));
        s3.put("day=2026-11-03/region=ap/deep/part-00000.jsonl", lines(4, 4));
        s3.put("day=2026-11-03/_SUCCESS", "");
        s3.put("day=2026-11-04/region=sa/part-00000.jsonl", lines(5, 5));

        assertThat(S3ItemReaderBuilder.childPrefixes(s3, BUCKET, "day=2026-11-03/"))
                .containsExactly("day=2026-11-03/region=ap/", "day=2026-11-03/region=eu/", "day=2026-11-03/region=us/");
        assertThat(S3ItemReaderBuilder.childPrefixes(s3, BUCKET, "")).containsExactly("day=2026-11-03/", "day=2026-11-04/");
        assertThat(S3ItemReaderBuilder.list(s3, BUCKET, "day=2026-11-04/"))
                .extracting(S3ItemReaderBuilder.ObjectRef::key).containsExactly("day=2026-11-04/region=sa/part-00000.jsonl");
    }

    @Test
    @DisplayName("an incomplete definition is refused, naming the reader")
    void definitionErrors() {
        assertThatThrownBy(() -> S3ItemReaderBuilder.<Line>of(s3, BUCKET).name("orders").jsonLines(Line.class).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("orders: exactly one of key(...) and prefix(...)");
        assertThatThrownBy(() -> S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("a").prefix("b/").jsonLines(Line.class).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one of");
        assertThatThrownBy(() -> S3ItemReaderBuilder.<Line>of(s3, BUCKET).key("a").build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("jsonLines(...), lines(...) or delegate(...)");
    }

    @Test
    @DisplayName("the listing's fingerprint follows keys and ETags, and tells a key from its neighbour")
    void fingerprints() {
        var a = new S3ItemReaderBuilder.ObjectRef("a", "1");
        var b = new S3ItemReaderBuilder.ObjectRef("b", "2");

        assertThat(S3ObjectsItemReader.fingerprint(List.of(a, b))).isEqualTo(S3ObjectsItemReader.fingerprint(List.of(a, b)))
                .isNotEqualTo(S3ObjectsItemReader.fingerprint(List.of(a)))
                .isNotEqualTo(S3ObjectsItemReader.fingerprint(List.of(a, new S3ItemReaderBuilder.ObjectRef("b", "3"))));
        assertThat(S3ObjectsItemReader.fingerprint(List.of(new S3ItemReaderBuilder.ObjectRef("a", "b1"))))
                .as("key 'a' with ETag 'b1' is not key 'ab' with ETag '1'")
                .isNotEqualTo(S3ObjectsItemReader.fingerprint(List.of(new S3ItemReaderBuilder.ObjectRef("ab", "1"))));
        assertThat(S3ObjectsItemReader.fingerprint(List.of(new S3ItemReaderBuilder.ObjectRef("a", null)))).hasSize(64);
    }
}
