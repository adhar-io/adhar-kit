package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.scheduler.SimpleEngines;
import com.adhar.kit.batch.scheduler.SimpleSchedulerEngine;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * {@link SchedulerEngineContract} on the engine {@code simple}, without its lock (a lock decides
 * between replicas, not what a slot is).
 */
class SimpleEngineContractTest extends SchedulerEngineContract {

    @Autowired
    JobOperator jobOperator;
    @Autowired
    JobResolver jobResolver;

    private SimpleSchedulerEngine simple;

    @Override
    void schedule(String bean, ZoneId zone) {
        properties.getScheduler().setTimeZone(zone);
        simple = new SimpleSchedulerEngine(jobOperator, repository, jobResolver, specs, resolver, null,
                properties.getScheduler(), Duration.ofSeconds(5), clock);
        simple.scheduleJob(bean, EVERY_FIVE_MINUTES);
    }

    @Override
    SlotOutcome fireSlot(String bean, Instant slot) {
        return SimpleEngines.runSlot(simple, bean, beans.getBean(bean, Job.class), slot);
    }

    @Override
    boolean recordsRunName() {
        return false;
    }
}
