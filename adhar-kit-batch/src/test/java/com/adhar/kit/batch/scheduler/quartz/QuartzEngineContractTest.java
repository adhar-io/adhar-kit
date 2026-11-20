package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.scheduler.SlotOutcome;
import org.quartz.TriggerKey;

import java.time.Instant;
import java.time.ZoneId;

/**
 * {@link SchedulerEngineContract} on the engine {@code quartz}: the schedule is the same
 * {@code adhar.batch.scheduler.jobs} cron, converted and stored by the reconcile, and a fire is the
 * one Quartz hands {@link BatchLaunchJob}.
 */
class QuartzEngineContractTest extends SchedulerEngineContract {

    @Override
    void schedule(String bean, ZoneId zone) {
        properties.getScheduler().setTimeZone(zone);
        properties.getScheduler().getJobs().put(bean, EVERY_FIVE_MINUTES);
        reconciled();
    }

    @Override
    SlotOutcome fireSlot(String bean, Instant slot) throws Exception {
        TriggerKey key = new TriggerKey(engine.scheduleNameOf(bean) + "." + QuartzSchedulerEngine.SCHEDULE_TRIGGER,
                QuartzSchedulerEngine.CONFIG_GROUP);
        return new BatchLaunchJob(engine).launch(fire(scheduler.getTrigger(key), slot));
    }

    @Override
    boolean recordsRunName() {
        return true;
    }
}
