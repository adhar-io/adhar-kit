package com.adhar.kit.batch.writer;

import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.reader.S3ItemReaderBuilder;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.RustFsStorage;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link S3ItemWriterBuilder} against RustFS: one object per committed chunk, a rolled-back chunk
 * gives its part number back, and a failed job restarted on the real step writes every item into
 * exactly one object.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3ItemWriterBuilderTestContainerTest {

    /** A line. */
    public record Line(long id) {
    }

    private S3Client s3;
    private String bucket;

    @BeforeEach
    void setUp() {
        s3 = RustFsStorage.client();
        bucket = RustFsStorage.createBucket("writer");
    }

    @AfterEach
    void tearDown() {
        s3.close();
    }

    private String object(String key) {
        return s3.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asUtf8String();
    }

    private List<Long> idsUnder(String prefix) {
        return S3ItemReaderBuilder.list(s3, bucket, prefix).stream()
                .flatMap(ref -> Arrays.stream(object(ref.key()).split("\n")))
                .map(line -> Long.parseLong(line.replaceAll("\\D", "")))
                .toList();
    }

    @Test
    @DisplayName("a rolled-back chunk gives its part number back, so its retry overwrites the same key")
    void rollbackGivesThePartBack() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("s3w-" + System.nanoTime()).build();
        try {
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(db));
            var writer = S3ItemWriterBuilder.<Line>of(s3, bucket).prefix("out/").name("w").build();
            ExecutionContext committed = new ExecutionContext();
            writer.open(committed);

            chunk(tx, writer, committed, false, new Line(1), new Line(2));
            assertThatThrownBy(() -> chunk(tx, writer, committed, true, new Line(3), new Line(4)))
                    .hasMessage("after the upload");
            chunk(tx, writer, committed, false, new Line(3), new Line(4));

            assertThat(S3ItemReaderBuilder.list(s3, bucket, "out/")).extracting(S3ItemReaderBuilder.ObjectRef::key)
                    .containsExactly("out/part-00000.jsonl", "out/part-00001.jsonl");
            assertThat(object("out/part-00001.jsonl")).isEqualTo("{\"id\":3}\n{\"id\":4}\n");
            assertThat(committed.getInt("w.next.part")).isEqualTo(2);

            var restarted = S3ItemWriterBuilder.<Line>of(s3, bucket).prefix("out/").name("w").build();
            restarted.open(committed);
            chunk(tx, restarted, committed, false, new Line(5));
            assertThat(object("out/part-00002.jsonl")).isEqualTo("{\"id\":5}\n");
        } finally {
            db.shutdown();
        }
    }

    /** One chunk as the step runs it: write and update in the transaction, persisted only on commit. */
    private static void chunk(TransactionTemplate tx, ItemStreamWriter<Line> writer, ExecutionContext committed,
                              boolean fail, Line... items) {
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
    @DisplayName("a job failing after an upload and then restarted writes every item into exactly one object")
    void restartedJobWritesEachItemOnce() throws Exception {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("test.bucket", bucket);
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(S3Job.class).properties(properties).run()) {
            TestJobs.seed(context.getBean(JdbcTemplate.class), 25);
            context.getBean(FailureInjector.class).failOn(15, 1);
            JobOperator operator = context.getBean(JobOperator.class);
            Job job = context.getBean("s3ExportJob", Job.class);
            JobParameters parameters = new JobParametersBuilder().addString("businessDate", "2026-11-03").toJobParameters();

            assertThat(operator.start(job, parameters).getStatus()).isEqualTo(BatchStatus.FAILED);
            // The failed chunk's object exists, and is about to be overwritten rather than duplicated.
            assertThat(S3ItemReaderBuilder.list(s3, bucket, "export/")).hasSize(2);

            assertThat(operator.start(job, parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }

        assertThat(S3ItemReaderBuilder.list(s3, bucket, "export/")).extracting(S3ItemReaderBuilder.ObjectRef::key)
                .containsExactly("export/part-00000.jsonl", "export/part-00001.jsonl", "export/part-00002.jsonl");
        List<Long> ids = idsUnder("export/");
        assertThat(ids).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a chunk retried after its upload overwrites its own part instead of adding one")
    void retriedChunkOverwritesItsPart() throws Exception {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("test.bucket", bucket);
        properties.put("test.retry", "true");
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(S3Job.class).properties(properties).run()) {
            TestJobs.seed(context.getBean(JdbcTemplate.class), 25);
            context.getBean(FailureInjector.class).failOn(15, 1);
            JobParameters parameters = new JobParametersBuilder().addString("businessDate", "2026-11-04").toJobParameters();

            assertThat(context.getBean(JobOperator.class).start(context.getBean("s3ExportJob", Job.class), parameters)
                    .getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }

        assertThat(S3ItemReaderBuilder.list(s3, bucket, "export/")).hasSize(3);
        assertThat(idsUnder("export/")).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a skip scan writes one part per surviving item and gives the failed item's part back")
    void skipScanWritesEachSurvivorOnce() throws Exception {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("test.bucket", bucket);
        properties.put("test.skip", "true");
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(S3Job.class).properties(properties).run()) {
            TestJobs.seed(context.getBean(JdbcTemplate.class), 25);
            context.getBean(FailureInjector.class).failOn(15, 2);
            JobParameters parameters = new JobParametersBuilder().addString("businessDate", "2026-11-05").toJobParameters();

            assertThat(context.getBean(JobOperator.class).start(context.getBean("s3ExportJob", Job.class), parameters)
                    .getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }

        List<Long> ids = idsUnder("export/");
        assertThat(ids).hasSize(24).doesNotHaveDuplicates().doesNotContain(15L);
    }

    @Test
    @DisplayName("an incomplete definition is refused")
    void definitionErrors() {
        assertThatThrownBy(() -> S3ItemWriterBuilder.of(s3, bucket).build()).hasMessageContaining("prefix(...)");
        assertThatThrownBy(() -> S3ItemWriterBuilder.of(s3, bucket).prefix("p/").partFormat("part.jsonl").build())
                .hasMessageContaining("must number the parts");
    }

    /** Copies SOURCE_ITEMS into S3 parts, failing after the upload where the injector says. */
    @Configuration(proxyBeanMethods = false)
    static class S3Job {

        @Bean
        S3Client s3Client() {
            return RustFsStorage.client();
        }

        @Bean
        FailureInjector failureInjector() {
            return new FailureInjector();
        }

        @Bean
        Step s3ExportStep(AdharSteps steps, DataSource dataSource, S3Client s3Client, FailureInjector failures,
                          @Value("${test.bucket}") String bucket, @Value("${test.retry:false}") boolean retry,
                          @Value("${test.skip:false}") boolean skip) {
            var reader = KeysetPagingItemReaderBuilder.of(dataSource, (rs, i) -> new Line(rs.getLong("ID")))
                    .from("SOURCE_ITEMS").key("ID").pageSize(TestJobs.CHUNK).name("source").build();
            S3PartItemWriter<Line> parts = S3ItemWriterBuilder.<Line>of(s3Client, bucket).prefix("export/").build();
            ItemStreamWriter<Line> writer = new ItemStreamWriter<>() {
                @Override
                public void open(ExecutionContext executionContext) {
                    parts.open(executionContext);
                }

                @Override
                public void update(ExecutionContext executionContext) {
                    parts.update(executionContext);
                }

                @Override
                public void write(Chunk<? extends Line> chunk) {
                    parts.write(chunk);
                    chunk.forEach(line -> failures.check(line.id()));
                }
            };
            return steps.<Line, Line>chunk("s3ExportStep", TestJobs.CHUNK).reader(reader).writer(writer)
                    .faultTolerance(retry ? FaultTolerance.retryOn(FailureInjector.InjectedFailure.class)
                            .backoff(Duration.ofMillis(1), Duration.ofMillis(1))
                            : skip ? FaultTolerance.none().skip(FailureInjector.InjectedFailure.class, 5) : FaultTolerance.none())
                    .build();
        }

        @Bean
        Job s3ExportJob(JobRepository jobRepository, Step s3ExportStep) {
            return new JobBuilder("s3ExportJob", jobRepository).start(s3ExportStep).build();
        }
    }
}
