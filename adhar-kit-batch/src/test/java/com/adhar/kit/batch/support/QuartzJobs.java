package com.adhar.kit.batch.support;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.step.AdharSteps;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The jobs of the Quartz engine's tests (KIT-Q.3).
 *
 * <ul>
 *   <li>{@value #CHUNK_JOB}: copies {@code SOURCE_ITEMS} into {@code TARGET_ITEMS} in chunks of
 *       {@value #CHUNK}, identified by its declared {@code run}. Its writer inserts the chunk and then
 *       passes the context's {@link Gate}, which can hold it mid-chunk, with the chunk's rows written
 *       but not committed: a held replica is the zombie a recovery must fence.</li>
 *   <li>The six tick jobs of {@link #TICK_JOBS}: tasklets recording their slot in {@code SLOT_RUNS}
 *       ({@code RUNNER} = the job, {@code SCHEDULED_TIME} = the slot).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Import({QuartzJobs.Ticks.class})
public class QuartzJobs {

    public static final String CHUNK_JOB = "chunkJob";

    public static final int CHUNK = 10;

    /** The tick jobs, one per trigger kind and misfire policy of AQ.4. */
    public static final List<String> TICK_JOBS = List.of("cronSkip", "cronLatest", "cronAll", "intervalSkip",
            "intervalLatest", "intervalAll");

    @Bean
    Gate gate() {
        return new Gate();
    }

    @Bean
    JdbcPagingItemReader<Long> chunkReader(DataSource dataSource) throws Exception {
        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReaderBuilder<Long>()
                .name("chunkReader")
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
    Step chunkStep(AdharSteps steps, JdbcPagingItemReader<Long> chunkReader, DataSource dataSource, Gate gate) {
        JdbcBatchItemWriter<Long> insert = new JdbcBatchItemWriterBuilder<Long>()
                .dataSource(dataSource)
                .sql("INSERT INTO TARGET_ITEMS (ID) VALUES (?)")
                .itemPreparedStatementSetter((id, ps) -> ps.setLong(1, id))
                .build();
        insert.afterPropertiesSet();
        ItemWriter<Long> writer = chunk -> {
            insert.write(chunk);
            for (Long id : chunk) {
                gate.pass(id);
            }
        };
        return steps.<Long, Long>chunk("chunkStep", CHUNK).reader(chunkReader).writer(writer).build();
    }

    @Bean
    @AdharBatchJob(description = "Copies the source rows", parameters = @JobParam(name = "run", required = true))
    Job chunkJob(JobRepository jobRepository, Step chunkStep) {
        return new JobBuilder(CHUNK_JOB, jobRepository).start(chunkStep).build();
    }

    /**
     * The tick jobs.
     */
    @Configuration(proxyBeanMethods = false)
    public static class Ticks {

        @Bean
        Job cronSkip(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("cronSkip", repository, steps, jdbc);
        }

        @Bean
        Job cronLatest(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("cronLatest", repository, steps, jdbc);
        }

        @Bean
        Job cronAll(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("cronAll", repository, steps, jdbc);
        }

        @Bean
        Job intervalSkip(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("intervalSkip", repository, steps, jdbc);
        }

        @Bean
        Job intervalLatest(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("intervalLatest", repository, steps, jdbc);
        }

        @Bean
        Job intervalAll(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return tick("intervalAll", repository, steps, jdbc);
        }

        private static Job tick(String name, JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return new JobBuilder(name, repository).start(steps.tasklet(name + "Step", (contribution, context) -> {
                String slot = context.getStepContext().getStepExecution().getJobExecution().getJobParameters()
                        .getString(BatchScheduler.SCHEDULED_TIME_PARAMETER, "none");
                jdbc.update("INSERT INTO SLOT_RUNS (RUNNER, SCHEDULED_TIME) VALUES (?, ?)", name, slot);
                return RepeatStatus.FINISHED;
            })).build();
        }
    }

    /**
     * Configured triggers for every tick job, as properties: cron and interval on the same grid of
     * {@code periodSeconds}, one per misfire policy.
     *
     * @param periodSeconds the grid's period, dividing 60
     * @return the {@code adhar.batch.quartz.triggers.*} properties
     */
    public static Map<String, Object> tickTriggers(int periodSeconds) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String job : TICK_JOBS) {
            String prefix = "adhar.batch.quartz.triggers." + job + "[0].";
            properties.put(prefix + "name", "tick");
            properties.put(prefix + "misfire", job.replace("cron", "").replace("interval", "").toLowerCase());
            if (job.startsWith("cron")) {
                properties.put(prefix + "kind", "cron");
                properties.put(prefix + "expression", "0/" + periodSeconds + " * * * * ?");
            } else {
                properties.put(prefix + "kind", "interval");
                properties.put(prefix + "every", periodSeconds + "s");
            }
        }
        return properties;
    }

    /**
     * Holds the chunk job's writer at one item, after the chunk's rows are written and before they
     * commit.
     */
    public static class Gate {

        private volatile long holdAt = -1;
        private volatile CountDownLatch reached = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(0);

        /**
         * @param item the item to hold at
         */
        public void holdAt(long item) {
            reached = new CountDownLatch(1);
            release = new CountDownLatch(1);
            holdAt = item;
        }

        /**
         * @param timeoutSeconds how long to wait
         * @return whether a writer is held
         */
        public boolean awaitHeld(long timeoutSeconds) throws InterruptedException {
            return reached.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        /**
         * @return whether a writer reached the item
         */
        public boolean held() {
            return reached.getCount() == 0;
        }

        /**
         * Lets the held writer go on.
         */
        public void release() {
            holdAt = -1;
            release.countDown();
        }

        void pass(long item) {
            if (item == holdAt) {
                reached.countDown();
                try {
                    release.await(5, TimeUnit.MINUTES);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
