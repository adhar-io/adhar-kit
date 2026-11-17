package com.adhar.kit.batch.writer;

import com.adhar.kit.batch.support.InMemoryS3Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link S3ItemWriterBuilder} and {@link S3PartItemWriter} without an object store: one object per
 * chunk, and a part number that is consumed only by a committed transaction, against
 * {@link InMemoryS3Client}. The same behaviour against RustFS, and inside real steps, is
 * {@link S3ItemWriterBuilderTestContainerTest}.
 */
class S3ItemWriterBuilderTest {

    private static final String BUCKET = "outbound";

    /** A line. */
    public record Line(long id) {
    }

    private final InMemoryS3Client s3 = new InMemoryS3Client(BUCKET);
    private final TransactionTemplate tx = new TransactionTemplate(new ResourcelessTransactionManager());

    /** One chunk as the step runs it: write and update in the transaction, persisted only on commit. */
    private void chunk(ItemStreamWriter<Line> writer, ExecutionContext committed, boolean fail, Line... items) {
        tx.executeWithoutResult(status -> {
            try {
                writer.write(Chunk.of(items));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            ExecutionContext pending = new ExecutionContext(committed);
            writer.update(pending);
            if (fail) {
                throw new IllegalStateException("after the upload");
            }
            pending.entrySet().forEach(entry -> committed.put(entry.getKey(), entry.getValue()));
        });
    }

    @Test
    @DisplayName("each chunk becomes one JSON Lines object, numbered from zero")
    void oneObjectPerChunk() {
        var writer = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("records/day=2026-11-03/").build();
        ExecutionContext context = new ExecutionContext();
        writer.open(context);

        writer.write(Chunk.of(new Line(1), new Line(2)));
        writer.write(new Chunk<>());
        writer.write(Chunk.of(new Line(3)));
        writer.update(context);

        assertThat(s3.keys()).containsExactly("records/day=2026-11-03/part-00000.jsonl",
                "records/day=2026-11-03/part-00001.jsonl");
        assertThat(s3.text("records/day=2026-11-03/part-00000.jsonl")).isEqualTo("{\"id\":1}\n{\"id\":2}\n");
        assertThat(s3.text("records/day=2026-11-03/part-00001.jsonl")).isEqualTo("{\"id\":3}\n");
        assertThat(s3.contentType("records/day=2026-11-03/part-00000.jsonl")).isEqualTo("application/jsonl");
        assertThat(s3.puts()).as("an empty chunk writes no object and takes no number").isEqualTo(2);
        assertThat(context.getInt("s3Writer.next.part")).isEqualTo(2);
        assertThat(writer.keyOf(12)).isEqualTo("records/day=2026-11-03/part-00012.jsonl");
    }

    @Test
    @DisplayName("a rolled-back chunk gives its part number back, so its retry overwrites the same key")
    void rollbackGivesThePartBack() {
        var writer = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("out/").name("w").build();
        ExecutionContext committed = new ExecutionContext();
        writer.open(committed);

        chunk(writer, committed, false, new Line(1), new Line(2));
        assertThatThrownBy(() -> chunk(writer, committed, true, new Line(3), new Line(4))).hasMessage("after the upload");
        assertThat(s3.text("out/part-00001.jsonl")).as("the failed attempt's object is left behind").isNotNull();
        chunk(writer, committed, false, new Line(3), new Line(5));

        assertThat(s3.keys()).containsExactly("out/part-00000.jsonl", "out/part-00001.jsonl");
        assertThat(s3.text("out/part-00001.jsonl")).isEqualTo("{\"id\":3}\n{\"id\":5}\n");
        assertThat(committed.getInt("w.next.part")).isEqualTo(2);
        assertThat(TransactionSynchronizationManager.hasResource(writer)).as("nothing stays bound to the thread").isFalse();
    }

    @Test
    @DisplayName("a write retried in the same transaction takes the part its first attempt uploaded")
    void aRetryInTheTransactionReusesItsPart() {
        var writer = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("out/").name("w").build();
        ExecutionContext committed = new ExecutionContext();
        writer.open(committed);
        chunk(writer, committed, false, new Line(1));

        tx.executeWithoutResult(status -> {
            writer.write(Chunk.of(new Line(2), new Line(3)));
            writer.write(Chunk.of(new Line(2), new Line(3)));
            writer.update(committed);
        });

        assertThat(s3.keys()).containsExactly("out/part-00000.jsonl", "out/part-00001.jsonl");
        assertThat(s3.puts()).isEqualTo(3);
        assertThat(committed.getInt("w.next.part")).isEqualTo(2);
    }

    @Test
    @DisplayName("a restart resumes at the number the last committed chunk recorded")
    void aRestartResumesItsNumbering() {
        ExecutionContext committed = new ExecutionContext();
        var first = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("out/").name("w").build();
        first.open(committed);
        chunk(first, committed, false, new Line(1));
        assertThatThrownBy(() -> chunk(first, committed, true, new Line(2))).hasMessage("after the upload");

        var restarted = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("out/").name("w").build();
        restarted.open(committed);
        chunk(restarted, committed, false, new Line(2), new Line(3));

        assertThat(s3.keys()).containsExactly("out/part-00000.jsonl", "out/part-00001.jsonl");
        assertThat(s3.text("out/part-00001.jsonl")).isEqualTo("{\"id\":2}\n{\"id\":3}\n");
    }

    @Test
    @DisplayName("another line format, part name and content type")
    void csvParts() {
        var writer = S3ItemWriterBuilder.<Line>of(s3, BUCKET).prefix("csv/").partFormat("lines-%03d.csv")
                .lines(line -> line.id() + ",ok", "text/csv").build();
        writer.open(new ExecutionContext());

        writer.write(Chunk.of(new Line(7), new Line(8)));

        assertThat(s3.text("csv/lines-000.csv")).isEqualTo("7,ok\n8,ok\n");
        assertThat(s3.contentType("csv/lines-000.csv")).isEqualTo("text/csv");
    }

    @Test
    @DisplayName("an incomplete definition is refused, naming the writer")
    void definitionErrors() {
        assertThatThrownBy(() -> S3ItemWriterBuilder.of(s3, BUCKET).name("export").build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("export: prefix(...) is required");
        assertThatThrownBy(() -> S3ItemWriterBuilder.of(s3, BUCKET).prefix("p/").partFormat("part.jsonl").build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must number the parts");
    }
}
