package com.adhar.kit.dapr;

import com.adhar.kit.dapr.jobs.JobDetails;
import com.adhar.kit.dapr.jobs.JobOptions;

import java.util.Objects;

/**
 * Dapr Jobs (alpha): durable, scheduled callbacks run by the Dapr scheduler. A job fires
 * {@code POST /job/<name>} on the app, which the kit routes to the {@code @DaprJob} handler of
 * that name.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprJobOperations {

    private final DaprFacade dapr;

    DaprJobOperations(DaprFacade dapr) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
    }

    public void schedule(String name, JobOptions options) {
        dapr.scheduleJob(name, options);
    }

    /** The job, or {@code null}. */
    public JobDetails get(String name) {
        return dapr.getJob(name);
    }

    public void delete(String name) {
        dapr.deleteJob(name);
    }
}
