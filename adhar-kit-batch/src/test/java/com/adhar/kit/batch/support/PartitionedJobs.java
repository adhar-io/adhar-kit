package com.adhar.kit.batch.support;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.partition.ConditionalOnBatchRole;
import com.adhar.kit.batch.partition.RemotePartitioning;
import com.adhar.kit.batch.partitioner.SqlKeyRangePartitioner;
import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.runtime.BatchRole;
import com.adhar.kit.batch.step.AdharSteps;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The remote-partitioned job of the partitioning tests, written the way the partitioned template
 * writes it (design 0025 §9.2): one configuration for every role. {@value #JOB} copies
 * {@code SOURCE_ITEMS} into {@code TARGET_ITEMS} over SQL key ranges in chunks of {@value #CHUNK};
 * the job and its manager step exist in every role, the worker step only where a worker runs.
 *
 * <p>Each chunk inserts its rows and then waits {@code test.chunk-millis} and passes the
 * {@link Gate}, both inside the chunk's transaction: a worker held at the gate has written rows that
 * only its commit makes real, which is the zombie of F19.</p>
 */
@Configuration(proxyBeanMethods = false)
public class PartitionedJobs {

    /** The job. */
    public static final String JOB = "partitionedImport";

    /** The manager step. */
    public static final String MANAGER_STEP = "importManager";

    /** The worker step: partitions are {@code importStep:partition<n>}. */
    public static final String WORKER_STEP = "importStep";

    /** Items per chunk. */
    public static final int CHUNK = 5;

    @Bean
    Gate gate() {
        return new Gate();
    }

    @Bean
    @StepScope
    @ConditionalOnBatchRole(BatchRole.WORKER)
    JdbcPagingItemReader<Long> rangeReader(DataSource dataSource,
                                           @Value("#{stepExecutionContext['minKey']}") Long minKey,
                                           @Value("#{stepExecutionContext['maxKey']}") Long maxKey) {
        return KeysetPagingItemReaderBuilder.of(dataSource, (rs, i) -> rs.getLong("ID"))
                .from("SOURCE_ITEMS").key("ID").where("ID BETWEEN :minKey AND :maxKey")
                .parameter("minKey", minKey).parameter("maxKey", maxKey).pageSize(CHUNK).name("range").build();
    }

    @Bean
    @ConditionalOnBatchRole(BatchRole.WORKER)
    Step importStep(AdharSteps steps, JdbcPagingItemReader<Long> rangeReader, JdbcTemplate jdbc, Gate gate,
                    @Value("${test.chunk-millis:0}") long chunkMillis) {
        ItemWriter<Long> writer = chunk -> {
            for (Long id : chunk) {
                jdbc.update("INSERT INTO TARGET_ITEMS (ID) VALUES (?)", id);
            }
            if (chunkMillis > 0) {
                Thread.sleep(chunkMillis);
            }
            for (Long id : chunk) {
                gate.pass(id);
            }
        };
        return steps.<Long, Long>chunk(WORKER_STEP, CHUNK).reader(rangeReader).writer(writer).build();
    }

    @Bean
    @AdharBatchJob(description = "Remote-partitioned import", parameters = @JobParam(name = "run", required = true))
    Job partitionedImport(JobRepository jobRepository, RemotePartitioning partitioning, DataSource dataSource) {
        return new JobBuilder(JOB, jobRepository).start(partitioning.manager(MANAGER_STEP, WORKER_STEP,
                SqlKeyRangePartitioner.of(dataSource, "SOURCE_ITEMS", "ID").build())).build();
    }

    /**
     * Holds the writer of one chosen item inside its chunk's transaction until released.
     */
    public static class Gate {

        private volatile long at = -1;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        /**
         * @param item hold the chunk that writes this item
         */
        public void holdAt(long item) {
            at = item;
        }

        void pass(long item) throws InterruptedException {
            if (item == at) {
                at = -1;
                reached.countDown();
                if (!released.await(2, TimeUnit.MINUTES)) {
                    throw new IllegalStateException("the gate was never released");
                }
            }
        }

        /**
         * @param timeout how long to wait
         * @return whether a writer is being held
         * @throws InterruptedException when interrupted
         */
        public boolean awaitHeld(Duration timeout) throws InterruptedException {
            return reached.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        /**
         * Lets the held writer go on to its commit.
         */
        public void release() {
            released.countDown();
        }
    }
}
