package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SimpleEngines;
import com.adhar.kit.batch.scheduler.SimpleSchedulerEngine;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.TriggerKey;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-Q.6, AQ.2 (design 0025 §5.20.2): a service that changes engine keeps every JobInstance. Slots
 * one engine ran are already run for the other, in both directions, on one JobRepository, and the
 * run names agree.
 */
class EngineSwitchContractTest extends QuartzEngineFixture {

    private static final String SCHEDULE = SchedulerEngineContract.EVERY_FIVE_MINUTES;
    private static final Instant FIVE_PAST = SchedulerEngineContract.FIVE_PAST;
    private static final Instant TEN_PAST = SchedulerEngineContract.TEN_PAST;
    private static final Instant QUARTER_PAST = Instant.parse("2026-11-06T12:15:00Z");

    @Autowired
    JobOperator jobOperator;
    @Autowired
    JobResolver jobResolver;

    private SimpleSchedulerEngine simple(String bean) {
        SimpleSchedulerEngine simple = new SimpleSchedulerEngine(jobOperator, repository, jobResolver, specs, resolver,
                null, properties.getScheduler(), Duration.ofSeconds(5), clock);
        simple.scheduleJob(bean, SCHEDULE);
        return simple;
    }

    private SlotOutcome simpleFires(String bean, Instant slot) {
        clock.set(slot);
        return SimpleEngines.runSlot(simple(bean), bean, beans.getBean(bean, Job.class), slot);
    }

    private SlotOutcome quartzFires(String bean, Instant slot) throws Exception {
        clock.set(slot);
        TriggerKey key = new TriggerKey(engine.scheduleNameOf(bean) + "." + QuartzSchedulerEngine.SCHEDULE_TRIGGER,
                QuartzSchedulerEngine.CONFIG_GROUP);
        return new BatchLaunchJob(engine).launch(fire(scheduler.getTrigger(key), slot));
    }

    @Test
    @DisplayName("simple to quartz and back: a slot run on one engine is already run on the other, and names alike")
    void switchingEngineKeepsEveryJobInstance() throws Exception {
        properties.getScheduler().setTimeZone(ZoneId.of("Asia/Singapore"));
        properties.getScheduler().getJobs().put(DATED_JOB, SCHEDULE);
        properties.getScheduler().getJobs().put("cronLatest", SCHEDULE);
        reconciled();

        assertThat(simpleFires("cronLatest", FIVE_PAST)).isEqualTo(SlotOutcome.RAN);
        assertThat(quartzFires("cronLatest", FIVE_PAST)).as("simple's slot, on quartz").isEqualTo(SlotOutcome.ALREADY_RAN);
        assertThat(quartzFires("cronLatest", TEN_PAST)).isEqualTo(SlotOutcome.RAN);
        assertThat(simpleFires("cronLatest", TEN_PAST)).as("quartz's slot, on simple").isEqualTo(SlotOutcome.ALREADY_RAN);
        assertThat(simpleFires("cronLatest", QUARTER_PAST)).isEqualTo(SlotOutcome.RAN);

        List<JobExecution> runs = executions("cronLatest");
        assertThat(runs).hasSize(3);
        assertThat(slots("cronLatest")).containsExactly("2026-11-06T12:05:00Z", "2026-11-06T12:10:00Z",
                "2026-11-06T12:15:00Z");
        JobExecution onQuartz = runs.get(1);
        assertThat(onQuartz.getJobParameters().getString(BatchScheduler.RUN_PARAMETER))
                .isEqualTo(RunIdentity.runName("cron-latest", SchedulerEngineContract.identifying(onQuartz)));
        assertThat(SchedulerEngineContract.identifying(runs.get(0)).keySet())
                .as("the same identifying parameters on both engines")
                .isEqualTo(SchedulerEngineContract.identifying(onQuartz).keySet());

        // A business date in the service's zone: 17:00Z is the next day in Singapore on both engines.
        Instant evening = Instant.parse("2026-11-06T17:00:00Z");
        assertThat(simpleFires(DATED_JOB, evening)).isEqualTo(SlotOutcome.RAN);
        assertThat(quartzFires(DATED_JOB, evening.plus(Duration.ofMinutes(5))))
                .as("another slot of the same business date is the same JobInstance").isEqualTo(SlotOutcome.ALREADY_RAN);
        assertThat(slots(DATED_JOB)).containsExactly("2026-11-07/sg");
    }
}
