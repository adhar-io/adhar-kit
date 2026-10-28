package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Handles a Dapr job (Dapr Jobs API, alpha): when the scheduler fires job {@link #name()}, the
 * sidecar calls {@code POST /job/<name>} and the kit runs this method with the job's data (as
 * {@code byte[]}, {@code String} or a JSON-mapped type, or no parameter).
 *
 * <p>With a {@link #schedule()} the kit also schedules the job when the application starts
 * (replacing a previous schedule of the same name), so a recurring task is one annotation:</p>
 * <pre>{@code
 * @DaprJob(name = "nightly-report", schedule = "0 0 2 * * *")
 * public void nightlyReport() { ... }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprJob {

    /**
     * The job name.
     */
    String name();

    /**
     * Schedule to register at startup: a six-field cron expression (seconds first) or
     * {@code @every 5m}, {@code @daily}, ...; empty to only handle a job scheduled elsewhere.
     */
    String schedule() default "";
}
