package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SlotIdentity;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-Q.6, the contract both engines of {@link BatchScheduler} keep (design 0025 §5.20.2, AQ.2): the
 * same job on the same schedule gives each slot the same identifying JobParameters and the same run
 * name (naming v1) on either engine, so a service that changes engine keeps every JobInstance. Each
 * case is held to fixed values, not to what one engine says of the other. The engine-switch half is
 * {@link EngineSwitchContractTest}; once per slot across replicas (AQ.1) is
 * {@code OncePerSlotTestContainerTest}.
 */
abstract class SchedulerEngineContract extends QuartzEngineFixture {

    /** The schedule both engines are given: Spring cron, as {@code adhar.batch.scheduler.jobs} takes it. */
    static final String EVERY_FIVE_MINUTES = "0 */5 * * * *";

    static final Instant FIVE_PAST = Instant.parse("2026-11-06T12:05:00Z");
    static final Instant TEN_PAST = Instant.parse("2026-11-06T12:10:00Z");

    /**
     * Schedules a job on {@link #EVERY_FIVE_MINUTES} on the engine under test, in the given zone.
     */
    abstract void schedule(String bean, ZoneId zone) throws Exception;

    /**
     * Fires one slot of a scheduled job on the engine under test, at the slot's time.
     */
    abstract SlotOutcome fireSlot(String bean, Instant slot) throws Exception;

    /**
     * @return whether the engine records the run name on the run ({@code adhar.run})
     */
    abstract boolean recordsRunName();

    /**
     * @param execution an execution
     * @return its identifying parameters as the text naming v1 hashes
     */
    static Map<String, String> identifying(JobExecution execution) {
        Map<String, String> values = new TreeMap<>();
        for (JobParameter<?> parameter : execution.getJobParameters().getIdentifyingParameters()) {
            values.put(parameter.name(), String.valueOf(parameter.value()));
        }
        return values;
    }

    private void assertRunName(JobExecution execution, String expected) {
        assertThat(RunIdentity.runName(SlotIdentity.dnsLabel(execution.getJobInstance().getJobName()),
                identifying(execution))).isEqualTo(expected);
        if (recordsRunName()) {
            assertThat(execution.getJobParameters().getString(BatchScheduler.RUN_PARAMETER)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("a job that declares no parameters: the slot's adhar.scheduled-time is the identity and names the run")
    void aSlotOfAnUndeclaredJob() throws Exception {
        schedule("cronLatest", ZoneId.of("UTC"));
        clock.set(FIVE_PAST);

        assertThat(fireSlot("cronLatest", FIVE_PAST)).isEqualTo(SlotOutcome.RAN);

        JobExecution run = executions("cronLatest").get(0);
        assertThat(run.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(identifying(run)).containsExactly(Map.entry(BatchScheduler.SCHEDULED_TIME_PARAMETER,
                "2026-11-06T12:05:00Z"));
        assertThat(run.getJobParameters().getString(BatchScheduler.TRIGGER_PARAMETER))
                .isEqualTo(BatchScheduler.EMBEDDED_TRIGGER);
        assertRunName(run, "cron-latest-" + RunIdentity.hash8(Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER,
                "2026-11-06T12:05:00Z")));
        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z");
    }

    @Test
    @DisplayName("a job with a schedule-sourced business date: the date in the zone is the identity, with no scheduled time")
    void aSlotOfABusinessDateJob() throws Exception {
        schedule(DATED_JOB, ZoneId.of("Asia/Singapore"));
        Instant slot = Instant.parse("2026-11-06T17:00:00Z");
        clock.set(slot);

        assertThat(fireSlot(DATED_JOB, slot)).isEqualTo(SlotOutcome.RAN);

        JobExecution run = executions(DATED_JOB).get(0);
        assertThat(identifying(run)).containsExactly(Map.entry("businessDate", "2026-11-07"), Map.entry("region", "sg"));
        assertRunName(run, RunIdentity.runName("dated-job", Map.of("businessDate", "2026-11-07", "region", "sg")));
        assertThat(slots(DATED_JOB)).containsExactly("2026-11-07/sg");
    }

    @Test
    @DisplayName("a slot fired twice is one JobInstance and one run; the next slot is another")
    void oncePerSlot() throws Exception {
        schedule("cronLatest", ZoneId.of("UTC"));
        clock.set(FIVE_PAST);

        assertThat(fireSlot("cronLatest", FIVE_PAST)).isEqualTo(SlotOutcome.RAN);
        assertThat(fireSlot("cronLatest", FIVE_PAST)).isEqualTo(SlotOutcome.ALREADY_RAN);
        clock.set(TEN_PAST);
        assertThat(fireSlot("cronLatest", TEN_PAST)).isEqualTo(SlotOutcome.RAN);

        List<JobExecution> runs = executions("cronLatest");
        assertThat(runs).hasSize(2);
        assertThat(runs).extracting(run -> run.getJobInstance().getInstanceId()).doesNotHaveDuplicates();
        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z", "2026-11-06T12:10:00Z");
    }

    @Test
    @DisplayName("two jobs on one slot are two runs with two names")
    void oneRunPerJobPerSlot() throws Exception {
        schedule("cronLatest", ZoneId.of("UTC"));
        schedule("cronAll", ZoneId.of("UTC"));
        clock.set(FIVE_PAST);

        assertThat(fireSlot("cronLatest", FIVE_PAST)).isEqualTo(SlotOutcome.RAN);
        assertThat(fireSlot("cronAll", FIVE_PAST)).isEqualTo(SlotOutcome.RAN);

        assertRunName(executions("cronAll").get(0), RunIdentity.runName("cron-all",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T12:05:00Z")));
    }
}
