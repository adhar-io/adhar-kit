package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties.Quartz.Misfire;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.runtime.JdbcExecutionLease;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.spi.OperableTrigger;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KIT-Q.3 (design 0025 §5.20.5): what {@link BatchLaunchJob} makes of one fire. Which slot it
 * stands for (a regular fire, a {@code FIRE_ONCE_NOW} misfire, an interval {@code RESCHEDULE_NOW}
 * misfire and its re-anchoring, a recovery fire), which identity it launches, and what the
 * identity's last execution turns the launch into. Real Spring Batch jobs on H2; the fires are
 * Quartz's own context objects (see {@link QuartzEngineFixture}).
 */
class BatchLaunchJobTest extends QuartzEngineFixture {

    private static final String EVERY_FIVE_MINUTES = "0 0/5 * * * ?";
    private static final Instant FIVE_PAST = NOON.plus(Duration.ofMinutes(5));
    private static final Instant SEVEN_PAST = NOON.plus(Duration.ofMinutes(7));
    private static final Instant TEN_PAST = NOON.plus(Duration.ofMinutes(10));

    private SlotOutcome launch(org.quartz.JobExecutionContext context) {
        return new BatchLaunchJob(engine).launch(context);
    }

    private JobExecution last(String job) {
        List<JobExecution> all = executions(job);
        return all.get(all.size() - 1);
    }

    // ------------------------------------------------------------------
    // Which slot a fire stands for
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a regular cron fire runs its slot, with the slot as identity and the provenance beside it")
    void aRegularFireRunsItsSlot() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        clock.set(FIVE_PAST);

        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z");
        JobExecution execution = last("cronLatest");
        JobParameters parameters = execution.getJobParameters();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(parameters.getIdentifyingParameters()).extracting(parameter -> parameter.name())
                .containsExactly(BatchScheduler.SCHEDULED_TIME_PARAMETER);
        assertThat(parameters.getString(BatchScheduler.TRIGGER_PARAMETER)).isEqualTo(BatchScheduler.EMBEDDED_TRIGGER);
        assertThat(parameters.getString(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER))
                .isEqualTo("adhar-batch-config.cron-latest.tick");
        assertThat(parameters.getString(QuartzSchedulerEngine.RUN_PARAMETER)).isEqualTo(RunIdentity.runName("cron-latest",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T12:05:00Z")));
        assertThat(parameters.getString(QuartzSchedulerEngine.INSTANCE_PARAMETER)).isEqualTo("replica-a");
        assertThat(parameters.getString(QuartzSchedulerEngine.APP_VERSION_PARAMETER)).isEqualTo("1.4.0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ADHAR_BATCH_EXECUTION_LEASE", Integer.class))
                .as("the lease is released when the run ends").isZero();
        assertThat(engine.runningJobs()).isEmpty();
    }

    @Test
    @DisplayName("a FIRE_ONCE_NOW misfire handled at 12:07:13 keeps the identity of the slot it missed, 12:05")
    void aFireOnceNowMisfireKeepsTheMissedSlot() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        Instant handled = SEVEN_PAST.plusSeconds(13).plusMillis(402);
        clock.set(handled);

        assertThat(launch(fire(stored("cronLatest"), handled))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z");
    }

    @Test
    @DisplayName("Quartz's own FIRE_ONCE_NOW moves the fire time to now; the identity stays on the expression's grid")
    void quartzFireOnceNowMovesTheFireTimeButNotTheIdentity() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        // Reconciled an hour ago, so the trigger's grid holds the slot the misfire stands for.
        clock.set(Instant.now().minus(Duration.ofHours(1)));
        reconciled();
        OperableTrigger trigger = (OperableTrigger) stored("cronLatest");
        Instant before = Instant.now();
        clock.set(before);

        trigger.updateAfterMisfire(null);

        Instant fireTime = trigger.getNextFireTime().toInstant();
        assertThat(fireTime).as("R12: FIRE_ONCE_NOW reschedules to the handling time").isAfterOrEqualTo(
                before.truncatedTo(ChronoUnit.MILLIS));
        assertThat(launch(fire(trigger, fireTime))).isEqualTo(SlotOutcome.RAN);
        long fiveMinutes = Duration.ofMinutes(5).toMillis();
        Instant slot = Instant.ofEpochMilli(Math.floorDiv(fireTime.toEpochMilli(), fiveMinutes) * fiveMinutes);
        assertThat(slots("cronLatest")).containsExactly(slot.toString());
    }

    @Test
    @DisplayName("an interval misfire handled at :07 on a 5-minute grid runs as :05 and fires next at :10")
    void anIntervalMisfireRunsAsItsGridSlotAndIsReanchored() throws Exception {
        interval("intervalLatest", "PT5M");
        reconciled();
        assertThat(stored("intervalLatest").getStartTime().toInstant()).isEqualTo(FIVE_PAST);
        // What RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT leaves in the store: startTime = the handling time.
        scheduler.rescheduleJob(configKey("intervalLatest"), stored("intervalLatest").getTriggerBuilder()
                .startAt(Date.from(SEVEN_PAST)).build());
        clock.set(SEVEN_PAST);
        Trigger moved = stored("intervalLatest");
        assertThat(SlotResolver.onGrid(moved)).isFalse();

        assertThat(launch(fire(moved, SEVEN_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("intervalLatest")).containsExactly("2026-11-06T12:05:00Z");
        Trigger anchored = stored("intervalLatest");
        assertThat(anchored.getStartTime().toInstant()).isEqualTo(TEN_PAST);
        assertThat(anchored.getNextFireTime().toInstant()).isEqualTo(TEN_PAST);
        assertThat(anchored.getFireTimeAfter(Date.from(TEN_PAST)).toInstant()).isEqualTo(NOON.plus(Duration.ofMinutes(15)));
        assertThat(anchored.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT);
        assertThat(anchored.getJobDataMap().getString(QuartzSchedulerEngine.GRID_START)).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("Quartz's own RESCHEDULE_NOW moves startTime off the grid; the run takes the grid's slot and re-anchors")
    void quartzRescheduleNowMovesTheStartTimeAndTheKitPutsItBack() throws Exception {
        interval("intervalLatest", "PT5M");
        reconciled();
        OperableTrigger trigger = (OperableTrigger) stored("intervalLatest");
        Instant before = Instant.now();
        clock.set(before);

        trigger.updateAfterMisfire(null);

        Instant handled = trigger.getStartTime().toInstant();
        assertThat(handled).as("R12: RESCHEDULE_NOW moves startTime to the handling time")
                .isAfterOrEqualTo(before.truncatedTo(ChronoUnit.MILLIS));
        assertThat(trigger.getNextFireTime().toInstant()).isEqualTo(handled);
        scheduler.rescheduleJob(trigger.getKey(), trigger);
        clock.set(handled);

        assertThat(launch(fire(stored("intervalLatest"), handled))).isEqualTo(SlotOutcome.RAN);

        long fiveMinutes = Duration.ofMinutes(5).toMillis();
        Instant slot = Instant.ofEpochMilli(Math.floorDiv(handled.toEpochMilli(), fiveMinutes) * fiveMinutes);
        assertThat(slots("intervalLatest")).containsExactly(slot.toString());
        assertThat(stored("intervalLatest").getStartTime().toInstant()).isEqualTo(slot.plusMillis(fiveMinutes));
        assertThat(SlotResolver.onGrid(stored("intervalLatest"))).isTrue();
    }

    @Test
    @DisplayName("an interval fire on the grid is not re-anchored")
    void aFireOnTheGridLeavesTheTriggerAlone() throws Exception {
        interval("intervalLatest", "PT5M");
        reconciled();
        clock.set(FIVE_PAST.plusMillis(30));

        assertThat(launch(fire(stored("intervalLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(stored("intervalLatest").getStartTime().toInstant()).isEqualTo(FIVE_PAST);
        assertThat(slots("intervalLatest")).containsExactly("2026-11-06T12:05:00Z");
    }

    @Test
    @DisplayName("misfire policy all: slots older than the max-catch-up most recent ones are skipped and counted")
    void catchUpIsBounded() throws Exception {
        cron("cronAll", EVERY_FIVE_MINUTES).setMisfire(Misfire.ALL);
        properties.getQuartz().setMaxCatchUp(2);
        reconciled();
        clock.set(NOON.plus(Duration.ofMinutes(21)));
        Trigger trigger = stored("cronAll");

        assertThat(launch(fire(trigger, FIVE_PAST))).isEqualTo(SlotOutcome.BEYOND_CATCH_UP);
        assertThat(launch(fire(trigger, TEN_PAST))).isEqualTo(SlotOutcome.BEYOND_CATCH_UP);
        assertThat(launch(fire(trigger, NOON.plus(Duration.ofMinutes(15))))).isEqualTo(SlotOutcome.RAN);
        assertThat(launch(fire(trigger, NOON.plus(Duration.ofMinutes(20))))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("cronAll")).containsExactly("2026-11-06T12:15:00Z", "2026-11-06T12:20:00Z");
        assertThat(engine.beyondCatchUp()).isEqualTo(2);
    }

    @Test
    @DisplayName("misfire policy all on an interval trigger: every missed slot runs with its own identity, oldest first")
    void anIntervalCatchUpRunsEverySlot() throws Exception {
        interval("intervalAll", "PT5M").setMisfire(Misfire.ALL);
        reconciled();
        clock.set(NOON.plus(Duration.ofMinutes(16)));
        Trigger trigger = stored("intervalAll");

        for (Instant slot : List.of(FIVE_PAST, TEN_PAST, NOON.plus(Duration.ofMinutes(15)))) {
            assertThat(launch(fire(trigger, slot))).isEqualTo(SlotOutcome.RAN);
        }

        assertThat(slots("intervalAll")).containsExactly("2026-11-06T12:05:00Z", "2026-11-06T12:10:00Z",
                "2026-11-06T12:15:00Z");
        assertThat(executions("intervalAll")).extracting(execution -> execution.getJobInstance().getId()).doesNotHaveDuplicates();
        assertThat(engine.beyondCatchUp()).isZero();
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a SCHEDULE business date is the slot's date in the trigger's zone, and later fires of that date are skipped")
    void aScheduledBusinessDateIdentifiesTheRun() throws Exception {
        cron(DATED_JOB, EVERY_FIVE_MINUTES).setTimeZone(ZoneId.of("Asia/Singapore"));
        reconciled();
        Instant lateEvening = Instant.parse("2026-11-06T23:30:00Z");
        clock.set(lateEvening);

        assertThat(launch(fire(stored(DATED_JOB), lateEvening))).isEqualTo(SlotOutcome.RAN);
        assertThat(launch(fire(stored(DATED_JOB), lateEvening.plus(Duration.ofMinutes(5)))))
                .as("rule 3: a trigger firing more often than its identity changes runs once per identity")
                .isEqualTo(SlotOutcome.ALREADY_RAN);

        assertThat(slots(DATED_JOB)).containsExactly("2026-11-07/sg");
        assertThat(executions(DATED_JOB)).hasSize(1);
        assertThat(last(DATED_JOB).getJobParameters().getParameter(BatchScheduler.SCHEDULED_TIME_PARAMETER)).isNull();
    }

    @Test
    @DisplayName("a now trigger has no slot: its identity is the caller's parameters and nothing else")
    void aNowTriggerRunsTheCallersParameters() throws Exception {
        reconciled();
        TestJobs.seed(jdbc, 25);
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        data.put(QuartzSchedulerEngine.PARAMS, "{\"run\":\"adhoc-1\"}");

        assertThat(launch(fire(oneShot(QuartzJobs.CHUNK_JOB, "adhoc-1", data), NOON))).isEqualTo(SlotOutcome.RAN);

        JobParameters parameters = last(QuartzJobs.CHUNK_JOB).getJobParameters();
        assertThat(parameters.getIdentifyingParameters()).extracting(parameter -> parameter.name()).containsExactly("run");
        assertThat(parameters.getString("run")).isEqualTo("adhoc-1");
        assertThat(parameters.getString(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER)).isEqualTo("adhar-batch-runtime.adhoc-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(25);
    }

    @Test
    @DisplayName("an at trigger's slot is the requested instant, whole seconds, never the time it fired")
    void anAtTriggerRunsTheRequestedInstant() throws Exception {
        reconciled();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_AT);
        data.put(QuartzSchedulerEngine.NOMINAL, "2026-11-06T09:30:00.700Z");
        clock.set(SEVEN_PAST);

        assertThat(launch(fire(oneShot("cronSkip", "at-1", data), SEVEN_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("cronSkip")).containsExactly("2026-11-06T09:30:00Z");
    }

    @Test
    @DisplayName("a resume trigger replays the stored identity verbatim: the same JobInstance, whatever the resolver says now")
    void aResumeTriggerReplaysItsIdentity() throws Exception {
        reconciled();
        Map<String, String> first = new LinkedHashMap<>();
        first.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        first.put(QuartzSchedulerEngine.PARAMS, "{\"businessDate\":\"2026-11-06\",\"region\":\"my\"}");
        assertThat(launch(fire(oneShot(DATED_JOB, "first", first), NOON))).isEqualTo(SlotOutcome.RAN);
        JobExecution stopped = last(DATED_JOB);
        // What a shutdown leaves: the execution and its step STOPPED.
        jdbc.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'STOPPED', EXIT_CODE = 'STOPPED' WHERE JOB_EXECUTION_ID = ?",
                stopped.getId());
        jdbc.update("UPDATE BATCH_STEP_EXECUTION SET STATUS = 'STOPPED', EXIT_CODE = 'STOPPED' WHERE JOB_EXECUTION_ID = ?",
                stopped.getId());
        Map<String, String> resume = new LinkedHashMap<>();
        resume.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_RESUME);
        resume.put(QuartzSchedulerEngine.IDENTITY, QuartzSchedulerEngine.encodeIdentity(stopped.getJobParameters()));
        resume.put(QuartzSchedulerEngine.RUN, "dated-job-0a1b2c3d");
        // Three days later, in another zone: a resolver would now derive another date, and the
        // region's default is "sg".
        resume.put(QuartzSchedulerEngine.ZONE, "Pacific/Auckland");
        clock.set(NOON.plus(Duration.ofDays(3)));

        assertThat(launch(fire(oneShot(DATED_JOB, "dated-job-0a1b2c3d", resume), clock.instant())))
                .isEqualTo(SlotOutcome.RAN);

        List<JobExecution> all = executions(DATED_JOB);
        assertThat(all).hasSize(2);
        assertThat(all.get(1).getJobInstance().getId()).isEqualTo(stopped.getJobInstance().getId());
        assertThat(all.get(1).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(all.get(1).getJobParameters().getString(QuartzSchedulerEngine.RUN_PARAMETER))
                .isEqualTo("dated-job-0a1b2c3d");
        assertThat(all.get(1).getJobParameters().getString(BatchScheduler.TRIGGER_PARAMETER))
                .isEqualTo(BatchScheduler.EMBEDDED_TRIGGER);
        assertThat(slots(DATED_JOB)).containsExactly("2026-11-06/my", "2026-11-06/my");
    }

    // ------------------------------------------------------------------
    // What the identity's last execution makes of the launch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a slot that already completed is skipped(already-complete)")
    void aCompletedSlotIsSkipped() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();

        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.ALREADY_RAN);

        assertThat(executions("cronLatest")).hasSize(1);
    }

    @Test
    @DisplayName("a FAILED identity is restarted by its next fire, in the same JobInstance")
    void aFailedIdentityIsRestarted() throws Exception {
        cron(FLAKY_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        Jobs.FLAKY.set(true);
        assertThat(launch(fire(stored(FLAKY_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);
        assertThat(last(FLAKY_JOB).getStatus()).isEqualTo(BatchStatus.FAILED);
        Jobs.FLAKY.set(false);

        assertThat(launch(fire(stored(FLAKY_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);

        List<JobExecution> all = executions(FLAKY_JOB);
        assertThat(all).extracting(JobExecution::getStatus).containsExactly(BatchStatus.FAILED, BatchStatus.COMPLETED);
        assertThat(all.get(1).getJobInstance().getId()).isEqualTo(all.get(0).getJobInstance().getId());
    }

    @Test
    @DisplayName("an ABANDONED identity never runs again: failed(abandoned)")
    void anAbandonedIdentityIsRefused() throws Exception {
        cron(FLAKY_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        Jobs.FLAKY.set(true);
        launch(fire(stored(FLAKY_JOB), FIVE_PAST));
        operator.abandon(last(FLAKY_JOB));
        Jobs.FLAKY.set(false);

        assertThat(launch(fire(stored(FLAKY_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.ABANDONED);

        assertThat(executions(FLAKY_JOB)).hasSize(1);
        assertThat(slots(FLAKY_JOB)).isEmpty();
    }

    /**
     * What a replica killed in its third chunk leaves: two chunks committed, the execution and its
     * step still STARTED, and the lease naming the replica.
     */
    private JobExecution orphanOf(String owner) throws Exception {
        TestJobs.seed(jdbc, 35);
        failures.failOn(25, 1);
        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);
        JobExecution failed = last(TestJobs.IMPORT_JOB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(20);
        jdbc.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'STARTED', EXIT_CODE = 'UNKNOWN', END_TIME = NULL"
                + " WHERE JOB_EXECUTION_ID = ?", failed.getId());
        jdbc.update("UPDATE BATCH_STEP_EXECUTION SET STATUS = 'STARTED', END_TIME = NULL WHERE JOB_EXECUTION_ID = ?",
                failed.getId());
        if (owner != null) {
            new JdbcExecutionLease(jdbc, "ADHAR_BATCH_EXECUTION_LEASE").acquire(failed.getId(),
                    new ExecutionLease.Owner(owner, "", ""));
        }
        return failed;
    }

    private void assertCompletedFromTheLastCommit(JobExecution orphan) {
        List<JobExecution> all = executions(TestJobs.IMPORT_JOB);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getStatus()).as("the orphan is recorded FAILED").isEqualTo(BatchStatus.FAILED);
        assertThat(all.get(1).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(all.get(1).getJobInstance().getId()).isEqualTo(orphan.getJobInstance().getId());
        assertThat(all.get(1).getStepExecutions().iterator().next().getReadCount())
                .as("restarted from the last commit: only the 15 rows after it are read").isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(35);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class))
                .as("no duplicate rows").isEqualTo(35);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ADHAR_BATCH_EXECUTION_LEASE", Integer.class)).isZero();
    }

    @Test
    @DisplayName("a recovery fire recovers the dead replica's STARTED execution and restarts it from the last commit")
    void aRecoveryFireRestartsFromTheLastCommit() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf("replica-dead");
        // Even with a fresh check-in of the owner: Quartz itself declared the replica dead.
        checkIn("replica-dead", SEVEN_PAST, 1000);
        clock.set(SEVEN_PAST);

        assertThat(launch(recoveryFire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RECOVERED);

        assertCompletedFromTheLastCommit(orphan);
        assertThat(last(TestJobs.IMPORT_JOB).getJobParameters().getString(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER))
                .as("the provenance names the trigger that fired originally, not Quartz's recovery trigger")
                .isEqualTo("adhar-batch-config.import-job.tick");
    }

    @Test
    @DisplayName("a recovery fire of a misfired fire takes its slot from the original trigger's grid")
    void aRecoveryFireOfAMisfireKeepsTheGridSlot() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf("replica-dead");
        clock.set(TEN_PAST.minusSeconds(1));

        // The dead replica's fire was a FIRE_ONCE_NOW handled at 12:07:13, so that is its scheduled time.
        assertThat(launch(recoveryFire(stored(TestJobs.IMPORT_JOB), SEVEN_PAST.plusSeconds(13))))
                .isEqualTo(SlotOutcome.RECOVERED);

        assertCompletedFromTheLastCommit(orphan);
    }

    @Test
    @DisplayName("a recovery fire whose original trigger was removed meanwhile still runs the slot Quartz recorded")
    void aRecoveryFireWithoutItsTriggerRunsTheRecordedTime() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        Trigger original = stored("cronLatest");
        scheduler.unscheduleJob(original.getKey());

        assertThat(launch(recoveryFire(original, FIVE_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z");
    }

    @Test
    @DisplayName("FQ6: an execution whose owner checked in within the margin is left alone, skipped(duplicate)")
    void aLiveOwnersRunIsADuplicate() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf("replica-b");
        clock.set(SEVEN_PAST);
        // A replica whose clock runs 20 s behind this one's: its newest check-in looks 20 s old.
        checkIn("replica-b", SEVEN_PAST.minusSeconds(20), 15_000);

        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.DUPLICATE);

        assertThat(executions(TestJobs.IMPORT_JOB)).hasSize(1);
        assertThat(repository.getJobExecution(orphan.getId()).getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT OWNER_POD FROM ADHAR_BATCH_EXECUTION_LEASE WHERE JOB_EXECUTION_ID = ?",
                String.class, orphan.getId())).as("the owner's lease is not touched").isEqualTo("replica-b");
    }

    @Test
    @DisplayName("an execution whose owner stopped checking in is recovered and restarted from the last commit")
    void aDeadOwnersRunIsRecovered() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf("replica-b");
        clock.set(SEVEN_PAST);
        checkIn("replica-b", SEVEN_PAST.minusSeconds(38), 15_000);

        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RECOVERED);

        assertCompletedFromTheLastCommit(orphan);
    }

    @Test
    @DisplayName("an execution whose owner has no check-in row at all is recovered")
    void anOwnerQuartzForgotIsDead() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf("replica-gone");

        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RECOVERED);

        assertCompletedFromTheLastCommit(orphan);
    }

    @Test
    @DisplayName("a running execution without a lease is a duplicate while it is fresh, and recovered once stale")
    void anUnownedRunIsJudgedByItsAge() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf(null);
        clock.set(Instant.now());

        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.DUPLICATE);
        assertThat(executions(TestJobs.IMPORT_JOB)).hasSize(1);

        clock.set(Instant.now().plus(properties.getRecovery().getStaleAfter()).plusSeconds(60));
        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RECOVERED);
        assertCompletedFromTheLastCommit(orphan);
    }

    @Test
    @DisplayName("a FAILED execution that left a step running is recovered first, then restarted")
    void aFailedExecutionWithARunningStepIsRecoveredFirst() throws Exception {
        cron(TestJobs.IMPORT_JOB, EVERY_FIVE_MINUTES);
        reconciled();
        JobExecution orphan = orphanOf(null);
        jdbc.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'FAILED', EXIT_CODE = 'FAILED', END_TIME = CURRENT_TIMESTAMP"
                + " WHERE JOB_EXECUTION_ID = ?", orphan.getId());

        assertThat(launch(fire(stored(TestJobs.IMPORT_JOB), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);

        assertCompletedFromTheLastCommit(orphan);
    }

    // ------------------------------------------------------------------
    // Fires that cannot start
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a fire whose parameters do not resolve does not start, and stores no execution")
    void unresolvableParametersDoNotStart() throws Exception {
        reconciled();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);

        assertThat(launch(fire(oneShot(QuartzJobs.CHUNK_JOB, "missing-run", data), NOON)))
                .isEqualTo(SlotOutcome.FAILED_TO_START);

        assertThat(executions(QuartzJobs.CHUNK_JOB)).isEmpty();
    }

    @Test
    @DisplayName("a trigger of a kind the kit does not know, or of a job this application lost, does not start")
    void unknownKindsAndJobsDoNotStart() throws Exception {
        reconciled();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, "weekly");
        assertThat(launch(fire(oneShot("cronSkip", "odd", data), NOON))).isEqualTo(SlotOutcome.FAILED_TO_START);

        JobKey ghost = new JobKey("ghost", QuartzSchedulerEngine.JOB_GROUP);
        scheduler.addJob(JobBuilder.newJob(BatchLaunchJob.class).withIdentity(ghost).storeDurably()
                .usingJobData(QuartzSchedulerEngine.JOB, "ghostJob").build(), true);
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        Trigger trigger = QuartzSchedulerEngine.oneShot(new TriggerKey("ghost-1", QuartzSchedulerEngine.RUNTIME_GROUP),
                ghost, data, NOON);
        assertThat(launch(fire(trigger, NOON))).isEqualTo(SlotOutcome.FAILED_TO_START);

        assertThat(slots("cronSkip")).isEmpty();
    }

    @Test
    @DisplayName("execute never throws: Quartz would refire or unschedule on an exception")
    void executeNeverThrows() throws Exception {
        reconciled();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_AT);
        data.put(QuartzSchedulerEngine.NOMINAL, "next tuesday");
        org.quartz.JobExecutionContext broken = fire(oneShot("cronSkip", "broken", data), NOON);

        assertThatCode(() -> new BatchLaunchJob(engine).execute(broken)).doesNotThrowAnyException();

        assertThat(executions("cronSkip")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Races with another replica, which only the launch itself can lose
    // ------------------------------------------------------------------

    private void engineOn(BatchOperator racing) {
        engine = new QuartzSchedulerEngine(scheduler, racing, repository, beans.getBeansOfType(Job.class), specs,
                resolver, ownership, jdbc, PREFIX, properties, null, clock);
        engine.afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("another replica starting the identity first turns the launch into a skip, not a failure")
    void losingTheRaceIsASkip() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        BatchOperator racing = mock(BatchOperator.class);
        when(racing.findLastExecution(anyString(), any())).thenReturn(Optional.empty());
        engineOn(racing);

        when(racing.start(any(Job.class), any())).thenThrow(new JobExecutionAlreadyRunningException("running"));
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.DUPLICATE);

        when(racing.start(any(Job.class), any())).thenThrow(new DataIntegrityViolationException("JOB_INST_UN"));
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.DUPLICATE);

        when(racing.start(any(Job.class), any())).thenThrow(new JobInstanceAlreadyCompleteException("done"));
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.ALREADY_RAN);

        when(racing.start(any(Job.class), any())).thenThrow(new IllegalStateException("the repository is gone"));
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.FAILED_TO_START);

        assertThat(engine.runningJobs()).as("a launch that did not start is not left as running").isEmpty();
    }

    @Test
    @DisplayName("a job on an asynchronous executor is waited for, owned while it runs and released at its end")
    void anAsynchronousRunIsAwaited() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        launch(fire(stored("cronLatest"), FIVE_PAST));
        JobExecution finished = last("cronLatest");
        // What an asynchronous launcher hands back: the execution, still STARTED.
        JobExecution handedBack = new JobExecution(finished.getId(), finished.getJobInstance(), finished.getJobParameters());
        handedBack.setStatus(BatchStatus.STARTED);
        BatchOperator async = mock(BatchOperator.class);
        when(async.findLastExecution(anyString(), any())).thenReturn(Optional.empty());
        when(async.start(any(Job.class), any())).thenAnswer(invocation -> {
            assertThat(engine.runningJobs()).containsExactly("cronLatest");
            return handedBack;
        });
        engineOn(async);

        assertThat(launch(fire(stored("cronLatest"), TEN_PAST))).isEqualTo(SlotOutcome.RAN);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ADHAR_BATCH_EXECUTION_LEASE", Integer.class))
                .as("the claimed lease is released").isZero();
        assertThat(engine.runningJobs()).isEmpty();
    }
}
