package com.adhar.kit.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Single entry point of the batch module: operations on jobs, the embedded scheduler, and the
 * module's defaults. Reached as {@code adhar.getBatch()} through {@code adhar-kit-starter}, or
 * directly as {@link #getInstance()}.
 *
 * <p>Self-initialising (K1): in a Spring application with at least one job, the module publishes
 * the facade before any application bean is created, bound to {@code adhar.batch.*} and backed by
 * the context's {@link BatchOperator} and (when {@code adhar.batch.scheduler.enabled}) its
 * {@link BatchScheduler}. Without such a context, {@link #getInstance()} returns a facade with the
 * defaults that has no operator and no scheduler; the operations then explain what is missing.</p>
 *
 * <pre>{@code
 * BatchFacade batch = BatchFacade.getInstance();      // or: BatchFacade batch = adhar.getBatch();
 * batch.scheduleJob("nightlyReport", "0 0 2 * * *");  // embedded mode only
 * BatchJobStats stats = batch.getJobStats("nightlyReport");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchFacade {

    private static volatile BatchFacade instance;

    private final BatchProperties properties;
    private final Supplier<BatchScheduler> scheduler;
    private final Supplier<BatchOperator> operator;

    /**
     * @param properties {@code adhar.batch.*}
     * @param scheduler  supplies the embedded scheduler, or {@code null} when it is disabled
     * @param operator   supplies the operator, or {@code null} without a JobRepository
     */
    public BatchFacade(BatchProperties properties, Supplier<BatchScheduler> scheduler,
                       Supplier<BatchOperator> operator) {
        this.properties = properties != null ? properties : new BatchProperties();
        this.scheduler = scheduler != null ? scheduler : () -> null;
        this.operator = operator != null ? operator : () -> null;
    }

    /**
     * The application's facade, or a facade with the defaults when no batch context runs.
     *
     * @return the facade, never {@code null}
     */
    public static BatchFacade getInstance() {
        BatchFacade current = instance;
        if (current == null) {
            synchronized (BatchFacade.class) {
                if (instance == null) {
                    instance = new BatchFacade(new BatchProperties(), null, null);
                    log.debug("BatchFacade initialised with the defaults (no batch application context)");
                }
                current = instance;
            }
        }
        return current;
    }

    /**
     * Makes a facade the one {@link #getInstance()} returns.
     *
     * @param facade the context's facade
     */
    static void publish(BatchFacade facade) {
        synchronized (BatchFacade.class) {
            instance = facade;
        }
    }

    /**
     * Withdraws a facade when its context closes, unless another one replaced it meanwhile.
     *
     * @param facade the context's facade
     */
    static void withdraw(BatchFacade facade) {
        synchronized (BatchFacade.class) {
            if (instance == facade) {
                instance = null;
            }
        }
    }

    // ----------------------------------------------------------------
    // Embedded scheduler
    // ----------------------------------------------------------------

    /**
     * Schedules a job in the embedded scheduler (replacing any schedule it has).
     *
     * @param jobName        the job's bean name or name
     * @param cronExpression six fields, seconds first, in {@code adhar.batch.scheduler.time-zone}
     * @throws IllegalStateException when the embedded scheduler is not enabled
     */
    public void scheduleJob(String jobName, String cronExpression) {
        requireScheduler().scheduleJob(jobName, cronExpression);
    }

    /**
     * @param jobName the name a job was scheduled under
     * @return {@code true} if it was scheduled
     * @throws IllegalStateException when the embedded scheduler is not enabled
     */
    public boolean cancelJob(String jobName) {
        return requireScheduler().cancelScheduledJob(jobName);
    }

    /**
     * @return the scheduled job names; empty when the embedded scheduler is not enabled
     */
    public Set<String> listScheduledJobs() {
        BatchScheduler current = scheduler.get();
        return current != null ? current.listScheduledJobs() : Collections.emptySet();
    }

    /**
     * @return the embedded scheduler, when enabled
     */
    public Optional<BatchScheduler> getScheduler() {
        return Optional.ofNullable(scheduler.get());
    }

    // ----------------------------------------------------------------
    // Operations
    // ----------------------------------------------------------------

    /**
     * @return the operator: start, restart, stop, abandon, recover, history
     * @throws IllegalStateException without a batch application context
     */
    public BatchOperator getOperator() {
        BatchOperator current = operator.get();
        if (current == null) {
            throw new IllegalStateException("No BatchOperator: the batch module runs in a Spring application "
                    + "that defines at least one Job (and adhar.batch.enabled is not false)");
        }
        return current;
    }

    /**
     * Statistics of a job from the JobRepository.
     *
     * @param jobName the job's name
     * @return executions, successes, failures, mean duration and latest start
     * @throws IllegalStateException without a batch application context
     */
    public BatchJobStats getJobStats(String jobName) {
        return getOperator().getJobStats(jobName);
    }

    // ----------------------------------------------------------------
    // Defaults
    // ----------------------------------------------------------------

    /**
     * @return {@code adhar.batch.chunk.size}
     */
    public int getDefaultChunkSize() {
        return properties.getChunk().getSize();
    }

    /**
     * @return {@code adhar.batch.page.size}, the page size of the kit's paging readers
     */
    public int getDefaultPageSize() {
        return properties.getPage().getSize();
    }

    /**
     * @return {@code adhar.batch.enabled}
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * @return a snapshot: enabled, whether an operator and a scheduler are available, the
     *         scheduled jobs and the jobs a scheduled run is executing
     */
    public Map<String, Object> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        BatchScheduler currentScheduler = scheduler.get();
        info.put("enabled", isEnabled());
        info.put("operatorAvailable", operator.get() != null);
        info.put("schedulerAvailable", currentScheduler != null);
        Set<String> scheduled = currentScheduler != null ? currentScheduler.listScheduledJobs() : Set.of();
        info.put("scheduledJobCount", scheduled.size());
        info.put("scheduledJobs", scheduled);
        info.put("runningScheduledJobs", currentScheduler != null ? currentScheduler.runningJobs() : Set.of());
        return Collections.unmodifiableMap(info);
    }

    private BatchScheduler requireScheduler() {
        BatchScheduler current = scheduler.get();
        if (current == null) {
            throw new IllegalStateException("The embedded batch scheduler is not enabled: set "
                    + "adhar.batch.scheduler.enabled=true (on the platform, schedule a BatchJob instead)");
        }
        return current;
    }
}
