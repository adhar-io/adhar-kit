package com.adhar.kit.batch.step;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;

/**
 * Entry point for building steps on the platform. Inject it (it is a bean whenever the
 * application has a job) instead of {@code new StepBuilder(...)}:
 *
 * <pre>{@code
 * @Bean
 * Step importStep(AdharSteps steps, ItemReader<InputRecord> reader,
 *                 RecordProcessor processor, ItemWriter<Record> writer) {
 *     return steps.<InputRecord, Record>chunk("importStep")      // size from adhar.batch.chunk.size
 *         .reader(reader).processor(processor).writer(writer)
 *         .faultTolerance(FaultTolerance.transientInfrastructure()
 *             .skip(ValidationException.class, 100))
 *         .build();
 * }
 * }</pre>
 *
 * <p>Every step it builds uses the JobRepository's transaction manager (so business writes and
 * progress commit together, INV-4), carries the kit's listeners, and reports to the application's
 * {@link ObservationRegistry} when there is one, whether or not the step is a bean.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class AdharSteps {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchListenerAttacher listeners;
    private final BatchProperties properties;
    private final ObservationRegistry observationRegistry;

    /**
     * @param jobRepository       the JobRepository
     * @param transactionManager  the JobRepository's transaction manager
     * @param listeners           attaches the kit's listeners
     * @param properties          {@code adhar.batch.*}
     * @param observationRegistry the application's observation registry, may be {@code null}
     */
    public AdharSteps(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                      BatchListenerAttacher listeners, BatchProperties properties,
                      ObservationRegistry observationRegistry) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.listeners = listeners;
        this.properties = properties;
        this.observationRegistry = observationRegistry;
    }

    /**
     * A chunk-oriented step with {@code adhar.batch.chunk.size} items per chunk.
     *
     * @param name the step name, unique within its job
     * @param <I>  the type read
     * @param <O>  the type written
     * @return the builder
     */
    public <I, O> AdharChunkStepBuilder<I, O> chunk(String name) {
        return chunk(name, properties.getChunk().getSize());
    }

    /**
     * A chunk-oriented step.
     *
     * @param name      the step name, unique within its job
     * @param chunkSize items per chunk (the commit interval)
     * @param <I>       the type read
     * @param <O>       the type written
     * @return the builder
     */
    public <I, O> AdharChunkStepBuilder<I, O> chunk(String name, int chunkSize) {
        Assert.hasText(name, "a step needs a name");
        Assert.isTrue(chunkSize > 0, "chunkSize must be positive");
        ChunkOrientedStepBuilder<I, O> builder = new StepBuilder(name, jobRepository).<I, O>chunk(chunkSize)
                .transactionManager(transactionManager);
        if (observationRegistry != null) {
            builder.observationRegistry(observationRegistry);
        }
        return new AdharChunkStepBuilder<>(builder, properties.getRetry(), listeners);
    }

    /**
     * A tasklet step on the JobRepository's transaction manager, with the kit's listeners.
     *
     * @param name    the step name, unique within its job
     * @param tasklet the work
     * @return the step
     */
    public TaskletStep tasklet(String name, Tasklet tasklet) {
        Assert.hasText(name, "a step needs a name");
        StepBuilder builder = step(name);
        TaskletStep step = builder.tasklet(tasklet, transactionManager).build();
        listeners.attach(step);
        return step;
    }

    /**
     * Spring Batch's own step builder on the kit's JobRepository, for the other kinds of step
     * (partition, flow, job). Attach the kit's listeners to what it builds with
     * {@link BatchListenerAttacher#attach(org.springframework.batch.core.step.Step)}, or declare
     * the step as a bean, which attaches them.
     *
     * @param name the step name
     * @return the builder
     */
    public StepBuilder step(String name) {
        StepBuilder builder = new StepBuilder(name, jobRepository);
        if (observationRegistry != null) {
            builder.observationRegistry(observationRegistry);
        }
        return builder;
    }

    /**
     * @return the transaction manager every step built here uses
     */
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * @return the JobRepository every step built here uses
     */
    public JobRepository getJobRepository() {
        return jobRepository;
    }
}
