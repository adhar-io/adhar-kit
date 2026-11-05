package com.adhar.kit.batch.metrics;

import com.adhar.kit.batch.listener.BatchRunObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The kit's batch meters: only what Spring Batch's own meters do not already say.
 *
 * <p>Spring Batch records {@code spring.batch.job}, {@code spring.batch.job.active},
 * {@code spring.batch.step}, {@code spring.batch.item.read}, {@code spring.batch.item.process}
 * and {@code spring.batch.chunk.write} through Spring Boot's observation support. The kit's first
 * release duplicated them as {@code adhar.batch.job.*} and {@code adhar.batch.step.*}; those are
 * gone. What remains:</p>
 * <ul>
 *   <li>{@value #RUN} - gauge, the number of running executions (1 while a run is in progress),
 *       tagged {@code job} and {@code trigger};</li>
 *   <li>{@value #LAST_COMMIT} - gauge, epoch seconds of a step's last committed chunk, tagged
 *       {@code job} and {@code step}: a stuck step is a gauge that stops moving;</li>
 *   <li>{@value #DEAD_LETTERED} - counter of items a step dead-lettered, tagged {@code job} and
 *       {@code step}.</li>
 * </ul>
 * <p>No business date or execution id is a tag: their cardinality is unbounded.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchMetrics implements BatchRunObserver {

    /** Running executions. */
    public static final String RUN = "adhar.batch.run";

    /** Epoch seconds of a step's last committed chunk. */
    public static final String LAST_COMMIT = "adhar.batch.last.commit";

    /** Items dead-lettered by a step. */
    public static final String DEAD_LETTERED = "adhar.batch.items.dead.lettered";

    /** Job parameter naming what started a run (design 0025 §5.6). */
    static final String TRIGGER_PARAMETER = "adhar.trigger";

    /** The trigger values of design 0025 §5.5; anything else is tagged {@code other}. */
    static final Set<String> TRIGGERS = Set.of("schedule", "manual", "restart", "backfill", "dependency",
            "workflow", "smoke", "ops", "embedded");

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<Tags, AtomicInteger> running = new ConcurrentHashMap<>();
    private final Map<Tags, AtomicLong> lastCommit = new ConcurrentHashMap<>();

    /**
     * @param registry the application's meter registry
     */
    public BatchMetrics(MeterRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    /**
     * @param registry the application's meter registry
     * @param clock    the time source of {@value #LAST_COMMIT}
     */
    public BatchMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    @Override
    public void jobStarted(JobExecution execution) {
        runGauge(execution).incrementAndGet();
    }

    @Override
    public void jobFinished(JobExecution execution) {
        runGauge(execution).updateAndGet(value -> Math.max(0, value - 1));
    }

    @Override
    public void chunkCommitted(StepExecution execution, long items) {
        Tags tags = Tags.of("job", execution.getJobExecution().getJobInstance().getJobName(),
                "step", execution.getStepName());
        lastCommit.computeIfAbsent(tags, key -> {
            AtomicLong holder = new AtomicLong();
            Gauge.builder(LAST_COMMIT, holder, AtomicLong::get)
                    .description("Epoch seconds of the step's last committed chunk")
                    .baseUnit("seconds")
                    .tags(key)
                    .strongReference(true)
                    .register(registry);
            return holder;
        }).set(clock.instant().getEpochSecond());
    }

    /**
     * Counts items a step wrote to its dead-letter store.
     *
     * @param jobName  the job
     * @param stepName the step
     * @param items    how many
     */
    public void recordDeadLettered(String jobName, String stepName, long items) {
        Counter.builder(DEAD_LETTERED)
                .description("Items a step dead-lettered")
                .tags("job", jobName, "step", stepName)
                .register(registry)
                .increment(items);
    }

    /**
     * @param execution a job execution
     * @return the {@code trigger} tag of its run: the {@value #TRIGGER_PARAMETER} parameter when it
     *         is one of {@link #TRIGGERS}, {@code other} for any other value, {@code unknown} when
     *         absent
     */
    static String triggerOf(JobExecution execution) {
        String trigger = execution.getJobParameters().getString(TRIGGER_PARAMETER);
        if (trigger == null || trigger.isBlank()) {
            return "unknown";
        }
        String normalized = trigger.trim().toLowerCase(Locale.ROOT);
        return TRIGGERS.contains(normalized) ? normalized : "other";
    }

    private AtomicInteger runGauge(JobExecution execution) {
        Tags tags = Tags.of("job", execution.getJobInstance().getJobName(), "trigger", triggerOf(execution));
        return running.computeIfAbsent(tags, key -> {
            AtomicInteger holder = new AtomicInteger();
            Gauge.builder(RUN, holder, AtomicInteger::get)
                    .description("Running executions of the job")
                    .tags(key)
                    .strongReference(true)
                    .register(registry);
            return holder;
        });
    }
}
