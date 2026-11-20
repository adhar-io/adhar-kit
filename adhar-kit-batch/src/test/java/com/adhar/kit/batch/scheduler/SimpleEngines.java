package com.adhar.kit.batch.scheduler;

import org.springframework.batch.core.job.Job;

import java.time.Instant;

/**
 * Lets tests in other packages drive one slot of the engine {@code simple}, as its threads do at the
 * slot's time (the engine contract suite runs it beside {@code quartz}).
 */
public final class SimpleEngines {

    private SimpleEngines() {
    }

    /**
     * @param engine       the engine
     * @param scheduleName the name the job was scheduled under
     * @param job          the job
     * @param nominal      the slot
     * @return what the fire did
     */
    public static SlotOutcome runSlot(SimpleSchedulerEngine engine, String scheduleName, Job job, Instant nominal) {
        return engine.runSlot(scheduleName, job, nominal);
    }
}
