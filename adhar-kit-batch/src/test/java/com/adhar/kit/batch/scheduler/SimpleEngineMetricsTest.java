package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParametersResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KIT-Q.7 (design 0025 §5.20.8): the engine {@code simple} records the same
 * {@code adhar.batch.scheduler.*} meters as {@code quartz}, under the schedule name both engines
 * give a job and the trigger {@value SimpleSchedulerEngine#TRIGGER}.
 */
class SimpleEngineMetricsTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private SimpleSchedulerEngine engine;

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.stop();
        }
    }

    @Test
    @DisplayName("a fire counts under the job's schedule name; the next fire is a gauge, removed with the schedule")
    void recordsFiresAndTheNextFire() throws Exception {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("importJob");
        JobResolver jobs = mock(JobResolver.class);
        when(jobs.resolve("importJob")).thenReturn(job);
        JobRepository repository = mock(JobRepository.class);
        JobOperator operator = mock(JobOperator.class);
        JobExecution completed = new JobExecution(1L, new JobInstance(7L, "importJob"), new JobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        when(operator.start(eq(job), any(JobParameters.class))).thenReturn(completed);
        BatchProperties.Scheduler settings = new BatchProperties.Scheduler();
        settings.setTimeZone(ZoneOffset.UTC);
        BatchProperties.Quartz naming = new BatchProperties.Quartz();
        BatchProperties.Quartz.JobSettings named = new BatchProperties.Quartz.JobSettings();
        named.setName("nightly-import");
        naming.getJobs().put("importJob", named);
        engine = new SimpleSchedulerEngine(operator, repository, jobs, null, new JobParametersResolver(), null, settings,
                Duration.ofSeconds(5), Clock.fixed(NOON, ZoneOffset.UTC));
        engine.observe(new MicrometerSchedulerMetrics(registry), naming);
        engine.scheduleJob("importJob", "0 */5 * * * *");

        engine.start();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(registry.find(SchedulerMetrics.FIRES)
                .tags("job", "nightly-import", "trigger", "schedule", "outcome", "ran").counter())
                .isNotNull().satisfies(counter -> assertThat(counter.count()).isEqualTo(1.0)));
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(registry.get(SchedulerMetrics.NEXT_FIRE)
                .tags("job", "nightly-import", "trigger", "schedule").gauge().value())
                .isEqualTo(NOON.plusSeconds(300).getEpochSecond()));
        assertThat(engine.scheduleNameOf("importJob")).isEqualTo("nightly-import");

        engine.cancelScheduledJob("importJob");
        assertThat(registry.find(SchedulerMetrics.NEXT_FIRE).gauges()).isEmpty();
    }
}
