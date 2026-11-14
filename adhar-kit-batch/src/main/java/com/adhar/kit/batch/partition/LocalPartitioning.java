package com.adhar.kit.batch.partition;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Partitions inside ONE run pod (design 0025 §5.10, Phase 1): a manager step splits the input with
 * a {@link Partitioner}, and {@code threads} threads run the worker step over the partitions. A
 * restart re-runs only the partitions that did not complete; each partition resumes from its own
 * last commit.
 *
 * <pre>{@code
 * @Bean
 * Step importManager(JobRepository jobRepository, DataSource dataSource, Step importStep) {
 *     return LocalPartitioning.of(8, 4).step("importManager", jobRepository,
 *             SqlKeyRangePartitioner.of(dataSource, "public.inbound_records", "id").build(), importStep);
 * }
 * }</pre>
 *
 * <p>The worker step must hold no state across partitions: its reader and writer are
 * {@code @StepScope} beans reading their range from {@code #{stepExecutionContext[...]}}. Size the
 * pod's CPU to {@code threads}, and the connection pool to at least {@code threads + 1} (each worker
 * holds a connection for its chunk transaction; the manager polls the repository).</p>
 *
 * @param gridSize partitions to create
 * @param threads  partitions run at the same time
 * @since 0.1.0
 */
public record LocalPartitioning(int gridSize, int threads) {

    /** Prefix of the worker threads' names, which the logs and thread dumps show. */
    public static final String THREAD_PREFIX = "batch-partition-";

    /**
     * @param gridSize partitions to create, at least 1
     * @param threads  partitions run at the same time, at least 1 and at most {@code gridSize}
     */
    public LocalPartitioning {
        if (gridSize < 1) {
            throw new IllegalArgumentException("gridSize must be at least 1, was " + gridSize);
        }
        if (threads < 1 || threads > gridSize) {
            throw new IllegalArgumentException("threads must be between 1 and gridSize (" + gridSize + "), was " + threads);
        }
    }

    /**
     * @param gridSize partitions to create
     * @param threads  partitions run at the same time
     * @return the preset
     */
    public static LocalPartitioning of(int gridSize, int threads) {
        return new LocalPartitioning(gridSize, threads);
    }

    /**
     * @param worker the step run once per partition
     * @return a handler running at most {@code threads} partitions at once
     */
    public TaskExecutorPartitionHandler handler(Step worker) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(THREAD_PREFIX);
        executor.setConcurrencyLimit(threads);
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(worker);
        handler.setGridSize(gridSize);
        handler.setTaskExecutor(executor);
        return handler;
    }

    /**
     * @param name          the manager step's name
     * @param jobRepository the JobRepository
     * @param partitioner   splits the input
     * @param worker        the step run once per partition
     * @return the manager step
     */
    public Step step(String name, JobRepository jobRepository, Partitioner partitioner, Step worker) {
        return new StepBuilder(name, jobRepository)
                .partitioner(worker.getName(), partitioner)
                .step(worker)
                .partitionHandler(handler(worker))
                .build();
    }
}
