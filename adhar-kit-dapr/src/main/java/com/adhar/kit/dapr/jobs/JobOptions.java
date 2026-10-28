package com.adhar.kit.dapr.jobs;

import java.time.Duration;
import java.time.Instant;

/**
 * When and how often a Dapr job fires (Dapr Jobs API, alpha), and what it carries.
 *
 * <p>Either a {@code schedule} (a cron expression with seconds, or {@code @every 1m},
 * {@code @daily}, ...) or a {@code dueTime} is required.</p>
 *
 * <pre>{@code
 * dapr.scheduleJob("nightly-report", JobOptions.cron("0 0 2 * * *").withData(Map.of("region", "sg")));
 * dapr.scheduleJob("reminder-42", JobOptions.at(Instant.now().plusSeconds(30)));
 * }</pre>
 *
 * @param schedule  cron/period expression, or {@code null}
 * @param dueTime   first (or only) run, or {@code null}
 * @param repeats   how many times to run, or {@code null} for unbounded
 * @param ttl       when the job expires, or {@code null}
 * @param data      the payload delivered to the job handler, or {@code null}
 * @param overwrite replace an existing job of the same name
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record JobOptions(String schedule, Instant dueTime, Integer repeats, Instant ttl, Object data,
                         boolean overwrite) {

    public JobOptions {
        if ((schedule == null || schedule.isBlank()) && dueTime == null) {
            throw new IllegalArgumentException("A job needs a schedule or a dueTime");
        }
    }

    /** A recurring job on a cron expression (six fields, seconds first) or {@code @every}. */
    public static JobOptions cron(String schedule) {
        return new JobOptions(schedule, null, null, null, null, false);
    }

    /** A job repeating every {@code period}. */
    public static JobOptions every(Duration period) {
        return cron("@every " + period.toSeconds() + "s");
    }

    /** A one-off job at {@code dueTime}. */
    public static JobOptions at(Instant dueTime) {
        return new JobOptions(null, dueTime, null, null, null, false);
    }

    public JobOptions withData(Object newData) {
        return new JobOptions(schedule, dueTime, repeats, ttl, newData, overwrite);
    }

    public JobOptions withRepeats(int newRepeats) {
        return new JobOptions(schedule, dueTime, newRepeats, ttl, data, overwrite);
    }

    public JobOptions withTtl(Instant newTtl) {
        return new JobOptions(schedule, dueTime, repeats, newTtl, data, overwrite);
    }

    public JobOptions withDueTime(Instant newDueTime) {
        return new JobOptions(schedule, newDueTime, repeats, ttl, data, overwrite);
    }

    public JobOptions overwriting() {
        return new JobOptions(schedule, dueTime, repeats, ttl, data, true);
    }
}
