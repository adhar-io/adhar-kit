package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import com.adhar.kit.batch.scheduler.quartz.BatchTriggers.Pending;
import com.adhar.kit.batch.scheduler.quartz.BatchTriggers.Result;
import com.adhar.kit.batch.scheduler.quartz.BatchTriggers.Status;
import com.adhar.kit.batch.support.QuartzJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.5, AQ.6 (design 0025 §5.20.5 rules 5-8): runtime triggers keyed by run name. Asking twice
 * for one identity stores one trigger and yields one run, before the first fired and after it
 * completed; an identity that cannot be resolved, or would be empty, throws before anything is
 * stored. The triggers are fired through {@link BatchLaunchJob} on Quartz's in-memory store.
 */
class BatchTriggersTest extends QuartzEngineFixture {

    private BatchTriggers triggers;

    @BeforeEach
    void triggers() {
        reconciled();
        triggers = new BatchTriggers(engine);
    }

    private Set<TriggerKey> runtimeTriggers() throws Exception {
        return scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.RUNTIME_GROUP));
    }

    private Trigger runtimeTrigger(String runName) throws Exception {
        return scheduler.getTrigger(new TriggerKey(runName, QuartzSchedulerEngine.RUNTIME_GROUP));
    }

    /** Fires a runtime trigger as Quartz does, and removes it as Quartz removes a fired one-shot trigger. */
    private SlotOutcome fireAndConsume(String runName) throws Exception {
        Trigger trigger = runtimeTrigger(runName);
        SlotOutcome outcome = new BatchLaunchJob(engine).launch(fire(trigger, clock.instant()));
        scheduler.unscheduleJob(trigger.getKey());
        return outcome;
    }

    @Test
    @DisplayName("AQ.6: fireNow twice with one identity stores one trigger keyed by the run name, before and after it ran")
    void fireNowIsIdempotentByRunName() throws Exception {
        String runName = RunIdentity.runName("chunk-job", Map.of("run", "r1"));

        Result first = triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r1"));
        Result second = triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r1"));

        assertThat(first).isEqualTo(new Result(runName, Status.SCHEDULED, null));
        assertThat(second).isEqualTo(new Result(runName, Status.PENDING, null));
        assertThat(runtimeTriggers()).containsExactly(new TriggerKey(runName, QuartzSchedulerEngine.RUNTIME_GROUP));

        assertThat(fireAndConsume(runName)).isEqualTo(SlotOutcome.RAN);
        JobExecution run = executions(QuartzJobs.CHUNK_JOB).get(0);
        assertThat(run.getJobParameters().getString(BatchScheduler.RUN_PARAMETER)).as("the launch names it alike")
                .isEqualTo(runName);
        assertThat(run.getJobParameters().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER))
                .as("a fireNow has no slot").isNull();

        Result after = triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r1"));
        assertThat(after).isEqualTo(new Result(runName, Status.COMPLETED, run.getId()));
        assertThat(runtimeTriggers()).as("nothing stored once it completed").isEmpty();
        assertThat(executions(QuartzJobs.CHUNK_JOB)).hasSize(1);
        assertThat(triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r2")).status())
                .as("another identity is another run").isEqualTo(Status.SCHEDULED);
    }

    @Test
    @DisplayName("AQ.6: a schedule-sourced parameter passed explicitly is the identity, without adhar.scheduled-time")
    void anExplicitScheduleParameter() throws Exception {
        Map<String, String> parameters = Map.of("businessDate", "2026-11-05");

        Result first = triggers.fireNow(DATED_JOB, parameters);
        Result second = triggers.fireNow(DATED_JOB, parameters);

        assertThat(first.runName()).isEqualTo(RunIdentity.runName("dated-job",
                Map.of("businessDate", "2026-11-05", "region", "sg")));
        assertThat(second.status()).isEqualTo(Status.PENDING);
        assertThat(fireAndConsume(first.runName())).isEqualTo(SlotOutcome.RAN);
        assertThat(slots(DATED_JOB)).containsExactly("2026-11-05/sg");
        assertThat(triggers.fireNow(DATED_JOB, parameters).status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    @DisplayName("AQ.6: an empty identity, invalid parameters or a missing schedule parameter throw before anything is stored")
    void invalidRequestsStoreNothing() throws Exception {
        assertThatThrownBy(() -> triggers.fireNow(FLAKY_JOB, Map.of()))
                .isInstanceOf(ParameterValidationException.class).hasMessageContaining("empty identity")
                .hasMessageContaining("fireAt");
        assertThatThrownBy(() -> triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r1", "colour", "red")))
                .isInstanceOf(ParameterValidationException.class).hasMessageContaining("colour is not a parameter");
        assertThatThrownBy(() -> triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of()))
                .isInstanceOf(ParameterValidationException.class).hasMessageContaining("run is required");
        assertThatThrownBy(() -> triggers.fireNow(DATED_JOB, Map.of("region", "us")))
                .isInstanceOf(ParameterValidationException.class)
                .hasMessageContaining("businessDate comes from the schedule");
        assertThatThrownBy(() -> triggers.fireNow("noSuchJob", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No job named [noSuchJob]");
        assertThatThrownBy(() -> triggers.fireNow(COUNTER_JOB, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JobParametersIncrementer");
        assertThatThrownBy(() -> triggers.fireAt(FLAKY_JOB, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fireAt needs an instant");

        assertThat(runtimeTriggers()).isEmpty();
    }

    @Test
    @DisplayName("AQ.6: fireAt is keyed on the requested instant, to the whole second: twice is one trigger and one run")
    void fireAtIsKeyedOnTheInstant() throws Exception {
        Instant at = Instant.parse("2026-11-06T15:00:00.750Z");
        String runName = RunIdentity.runName("flaky-job",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T15:00:00Z"));

        Result first = triggers.fireAt(FLAKY_JOB, at, null);
        Result second = triggers.fireAt(FLAKY_JOB, at.minusMillis(500), Map.of());

        assertThat(first).isEqualTo(new Result(runName, Status.SCHEDULED, null));
        assertThat(second.status()).isEqualTo(Status.PENDING);
        Trigger stored = runtimeTrigger(runName);
        assertThat(stored.getStartTime().toInstant()).isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));
        assertThat(stored.getMisfireInstruction()).isEqualTo(org.quartz.SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        assertThat(stored.getJobDataMap().getString(QuartzSchedulerEngine.KIND)).isEqualTo(QuartzSchedulerEngine.KIND_AT);

        clock.set(Instant.parse("2026-11-06T15:07:00Z"));
        assertThat(fireAndConsume(runName)).isEqualTo(SlotOutcome.RAN);
        assertThat(executions(FLAKY_JOB).get(0).getJobParameters().getString(BatchScheduler.SCHEDULED_TIME_PARAMETER))
                .as("the identity is the requested instant, not the late fire").isEqualTo("2026-11-06T15:00:00Z");
        assertThat(triggers.fireAt(FLAKY_JOB, at, Map.of()).status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    @DisplayName("an identity that is running is PENDING, with its execution: nothing new is stored")
    void aRunningIdentityIsPending() throws Exception {
        JobParameters identity = new JobParametersBuilder().addString("run", "r9").toJobParameters();
        JobInstance instance = repository.createJobInstance(QuartzJobs.CHUNK_JOB, identity);
        JobExecution running = repository.createJobExecution(instance, identity, new ExecutionContext());

        Result result = triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r9"));

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.executionId()).isEqualTo(running.getId());
        assertThat(runtimeTriggers()).isEmpty();
    }

    @Test
    @DisplayName("a FAILED identity is stored again: its next fire restarts it")
    void aFailedIdentityIsStoredAgain() throws Exception {
        Jobs.FLAKY.set(true);
        Instant at = Instant.parse("2026-11-06T15:00:00Z");
        String runName = triggers.fireAt(FLAKY_JOB, at, Map.of()).runName();
        fireAndConsume(runName);

        assertThat(triggers.fireAt(FLAKY_JOB, at, Map.of()).status()).isEqualTo(Status.SCHEDULED);
    }

    @Test
    @DisplayName("list shows each waiting runtime trigger by run name with its job, kind, time and parameters; cancel removes one")
    void listAndCancel() throws Exception {
        Instant at = Instant.parse("2026-11-07T08:00:00Z");
        String later = triggers.fireAt(DATED_JOB, at, Map.of("region", "us")).runName();
        String now = triggers.fireNow(QuartzJobs.CHUNK_JOB, Map.of("run", "r1")).runName();

        List<Pending> pending = triggers.list();
        Map<String, Pending> byRun = new LinkedHashMap<>();
        pending.forEach(p -> byRun.put(p.runName(), p));

        assertThat(pending).extracting(Pending::runName).isSorted().containsExactlyInAnyOrder(later, now);
        assertThat(byRun.get(later)).isEqualTo(new Pending(later, DATED_JOB, QuartzSchedulerEngine.KIND_AT, at, at,
                Map.of("region", "us")));
        assertThat(byRun.get(now)).isEqualTo(new Pending(now, QuartzJobs.CHUNK_JOB, QuartzSchedulerEngine.KIND_NOW,
                NOON, null, Map.of("run", "r1")));

        assertThat(triggers.cancel(later)).isTrue();
        assertThat(triggers.cancel(later)).isFalse();
        assertThat(triggers.list()).extracting(Pending::runName).containsExactly(now);
    }
}
