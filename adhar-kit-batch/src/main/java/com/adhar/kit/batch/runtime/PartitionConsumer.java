package com.adhar.kit.batch.runtime;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.function.BooleanSupplier;

/**
 * What a worker pod of a remote-partitioned job does instead of starting the job (design 0025
 * §5.10): it claims the partition requests of its run's identity and executes them until the
 * manager's execution of that identity completes. The implementation lives with the transport
 * ({@code com.adhar.kit.batch.partition.PartitionWorker}); it exists only when Spring Batch
 * Integration and Spring Integration JDBC are on the classpath.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@FunctionalInterface
public interface PartitionConsumer {

    /**
     * @param job        the job whose partitions to serve
     * @param parameters the run's resolved parameters, computed exactly as the manager computes
     *                   them: their identifying part names the partition channel's group
     * @param context    the run
     * @param stopping   whether the pod is terminating: no new request is taken once it is
     * @return how the worker's part of the run ended
     */
    RunOutcome consume(Job job, JobParameters parameters, RunContext context, BooleanSupplier stopping);
}
