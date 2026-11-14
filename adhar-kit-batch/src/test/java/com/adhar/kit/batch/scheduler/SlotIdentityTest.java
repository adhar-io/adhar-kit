package com.adhar.kit.batch.scheduler;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpec.Param;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.params.RunIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KIT-Q.1 (design 0025 §5.20.5 identity rules 1-2): {@link SlotIdentity} is the one function both
 * engines derive a slot's JobParameters from. A SCHEDULE-sourced identifying business date REPLACES
 * {@value BatchScheduler#SCHEDULED_TIME_PARAMETER}; a job that declares none keeps it.
 */
class SlotIdentityTest {

    private static final Instant SLOT = Instant.parse("2026-11-06T17:30:00.400Z");

    private static final JobParametersResolver RESOLVER = new JobParametersResolver();

    private static final JobParameterSpec DAILY = new JobParameterSpec("dailyReport", "", List.of(
            new Param("businessDate", ParamType.DATE, true, false, null, null, List.of(), ParamSource.SCHEDULE,
                    false, ""),
            new Param("region", ParamType.STRING, true, false, "sg", null, List.of(), ParamSource.INPUT, false, "")),
            false);

    private static final JobParameterSpec UNDATED = new JobParameterSpec("poll", "", List.of(
            new Param("region", ParamType.STRING, true, false, "sg", null, List.of(), ParamSource.INPUT, false, "")),
            false);

    private static final JobParameterSpec DATE_NOT_IDENTIFYING = new JobParameterSpec("audit", "", List.of(
            new Param("businessDate", ParamType.DATE, false, false, null, null, List.of(), ParamSource.SCHEDULE,
                    false, "")),
            false);

    @Test
    @DisplayName("a SCHEDULE-sourced identifying businessDate replaces adhar.scheduled-time")
    void scheduleSourcedDateReplacesTheScheduledTime() {
        SlotIdentity identity = SlotIdentity.resolve(RESOLVER, DAILY, Map.of(), SLOT, ZoneOffset.UTC);

        assertThat(identity.parameters().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER)).isNull();
        assertThat(identity.parameters().getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(identity.identifying()).containsExactlyInAnyOrderEntriesOf(
                Map.of("businessDate", "2026-11-06", "region", "sg"));
        assertThat(identity.parameters().getString(BatchScheduler.TRIGGER_PARAMETER))
                .isEqualTo(BatchScheduler.EMBEDDED_TRIGGER);
    }

    @Test
    @DisplayName("the business date is the nominal date in the TRIGGER's zone, as for a CronJob run")
    void theBusinessDateIsTakenInTheTriggersZone() {
        SlotIdentity singapore = SlotIdentity.resolve(RESOLVER, DAILY, Map.of(), SLOT, ZoneId.of("Asia/Singapore"));

        assertThat(singapore.parameters().getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("a job that declares no identifying SCHEDULE parameter keeps adhar.scheduled-time, whole seconds")
    void noneDeclaredKeepsTheScheduledTime() {
        SlotIdentity undated = SlotIdentity.resolve(RESOLVER, UNDATED, Map.of(), SLOT, ZoneOffset.UTC);
        SlotIdentity nonIdentifying = SlotIdentity.resolve(RESOLVER, DATE_NOT_IDENTIFYING, Map.of(), SLOT,
                ZoneOffset.UTC);
        SlotIdentity undeclared = SlotIdentity.of(JobParameterSpec.none("job"), SlotIdentity.UNDECLARED, SLOT);

        for (SlotIdentity identity : List.of(undated, nonIdentifying, undeclared)) {
            assertThat(identity.parameters().getString(BatchScheduler.SCHEDULED_TIME_PARAMETER))
                    .isEqualTo("2026-11-06T17:30:00Z");
            assertThat(identity.parameters().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER).identifying())
                    .isTrue();
            assertThat(identity.identifying()).containsEntry(BatchScheduler.SCHEDULED_TIME_PARAMETER,
                    "2026-11-06T17:30:00Z");
        }
        assertThat(undated.identifying()).containsEntry("region", "sg");
    }

    @Test
    @DisplayName("a fire with no nominal time carries the resolved parameters only")
    void noNominalTimeNoScheduledTime() {
        SlotIdentity identity = SlotIdentity.resolve(RESOLVER, UNDATED, Map.of("region", "my"), null, ZoneOffset.UTC);

        assertThat(identity.parameters().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER)).isNull();
        assertThat(identity.identifying()).containsExactlyEntriesOf(Map.of("region", "my"));
    }

    @Test
    @DisplayName("without a nominal time a SCHEDULE parameter must be given, never taken from now()")
    void noNominalTimeNeedsTheScheduleParameter() {
        assertThatThrownBy(() -> SlotIdentity.resolve(RESOLVER, DAILY, Map.of(), null, ZoneOffset.UTC))
                .isInstanceOf(ParameterValidationException.class)
                .hasMessageContaining("businessDate");
    }

    @Test
    @DisplayName("the run name is naming v1 over the identifying values, adhar.scheduled-time included")
    void runNameIsNamingV1() {
        SlotIdentity identity = SlotIdentity.of(JobParameterSpec.none("job"), SlotIdentity.UNDECLARED, SLOT);

        assertThat(identity.runName("import-job")).isEqualTo(RunIdentity.runName("import-job",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T17:30:00Z")));
    }

    @Test
    @DisplayName("the engine simple resolves declared parameters through SlotIdentity (D9)")
    void theSimpleEngineResolvesDeclaredParameters() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        JobRepository jobRepository = mock(JobRepository.class);
        JobResolver jobResolver = mock(JobResolver.class);
        SchedulerLock lock = mock(SchedulerLock.class);
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("dailyReport");
        when(lock.tryLock(eq("dailyReport"), any(Duration.class))).thenReturn(true);
        JobExecution completed = new JobExecution(1L, new JobInstance(1L, "dailyReport"), new JobParameters());
        completed.setStatus(org.springframework.batch.core.BatchStatus.COMPLETED);
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(completed);
        SimpleSchedulerEngine engine = new SimpleSchedulerEngine(jobOperator, jobRepository, jobResolver,
                JobParameterSpecRegistry.of(List.of(DAILY)), RESOLVER, lock, new BatchProperties.Scheduler(),
                Duration.ofSeconds(5), Clock.fixed(SLOT, ZoneOffset.UTC));

        assertThat(engine.runSlot("dailyReport", job, SLOT)).isEqualTo(SlotOutcome.RAN);

        ArgumentCaptor<JobParameters> started = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(job), started.capture());
        assertThat(started.getValue().getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(started.getValue().getString("region")).isEqualTo("sg");
        assertThat(started.getValue().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER)).isNull();
    }
}
