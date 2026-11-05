package com.adhar.kit.batch.support;

import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.step.AdharSteps;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The jobs of the tests.
 *
 * <ul>
 *   <li>{@value #IMPORT_JOB}: copies {@code SOURCE_ITEMS} into {@code TARGET_ITEMS} in chunks of
 *       {@value #CHUNK}; its writer inserts the chunk and THEN may throw (the
 *       {@link FailureInjector}), so a failed chunk's rows exist in the transaction when it
 *       fails: only a shared transaction (INV-4) rolls them back.</li>
 *   <li>{@value #SLOW_JOB}: a tasklet that records its slot in {@code SLOT_RUNS} and then sleeps
 *       {@code test.slow-job.sleep} (default 1500 ms).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class TestJobs {

    /** The chunk job. */
    public static final String IMPORT_JOB = "importJob";

    /** The tasklet job of the scheduler tests. */
    public static final String SLOW_JOB = "slowJob";

    /** Items per chunk of the import job. */
    public static final int CHUNK = 10;

    @Bean
    FailureInjector failureInjector() {
        return new FailureInjector();
    }

    @Bean
    JdbcPagingItemReader<Long> sourceReader(DataSource dataSource) throws Exception {
        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReaderBuilder<Long>()
                .name("sourceReader")
                .dataSource(dataSource)
                .selectClause("SELECT ID")
                .fromClause("FROM SOURCE_ITEMS")
                .sortKeys(Map.of("ID", Order.ASCENDING))
                .rowMapper((rs, row) -> rs.getLong(1))
                .pageSize(CHUNK)
                .build();
        reader.afterPropertiesSet();
        return reader;
    }

    @Bean
    Step importStep(AdharSteps steps, JdbcPagingItemReader<Long> sourceReader, DataSource dataSource,
                    FailureInjector failures) throws Exception {
        JdbcBatchItemWriter<Long> insert = new JdbcBatchItemWriterBuilder<Long>()
                .dataSource(dataSource)
                .sql("INSERT INTO TARGET_ITEMS (ID) VALUES (?)")
                .itemPreparedStatementSetter((id, ps) -> ps.setLong(1, id))
                .build();
        insert.afterPropertiesSet();
        ItemWriter<Long> writer = chunk -> {
            insert.write(chunk);
            for (Long id : chunk) {
                failures.check(id);
            }
        };
        return steps.<Long, Long>chunk("importStep", CHUNK).reader(sourceReader).writer(writer).build();
    }

    @Bean
    Job importJob(JobRepository jobRepository, Step importStep) {
        return new JobBuilder(IMPORT_JOB, jobRepository).start(importStep).build();
    }

    @Bean
    TaskletStep slowStep(AdharSteps steps, JdbcTemplate jdbcTemplate,
                         @Value("${test.runner:runner}") String runner,
                         @Value("${test.slow-job.sleep:1500}") long sleepMs) {
        return steps.tasklet("slowStep", (contribution, chunkContext) -> {
            String slot = chunkContext.getStepContext().getStepExecution().getJobExecution().getJobParameters()
                    .getString(BatchScheduler.SCHEDULED_TIME_PARAMETER, "none");
            jdbcTemplate.update("INSERT INTO SLOT_RUNS (RUNNER, SCHEDULED_TIME) VALUES (?, ?)", runner, slot);
            Thread.sleep(sleepMs);
            return RepeatStatus.FINISHED;
        });
    }

    @Bean
    Job slowJob(JobRepository jobRepository, TaskletStep slowStep) {
        return new JobBuilder(SLOW_JOB, jobRepository).start(slowStep).build();
    }

    /**
     * @param jdbcTemplate the test database
     * @param count        how many source rows to create (ids 1..count)
     */
    public static void seed(JdbcTemplate jdbcTemplate, int count) {
        jdbcTemplate.update("DELETE FROM SOURCE_ITEMS");
        jdbcTemplate.update("DELETE FROM TARGET_ITEMS");
        for (long id = 1; id <= count; id++) {
            jdbcTemplate.update("INSERT INTO SOURCE_ITEMS (ID) VALUES (?)", id);
        }
    }
}
