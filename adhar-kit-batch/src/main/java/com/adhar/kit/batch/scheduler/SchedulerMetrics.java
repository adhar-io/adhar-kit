package com.adhar.kit.batch.scheduler;

import java.time.Instant;

/**
 * The in-service scheduler's meters, on both engines of {@link BatchScheduler} (design 0025
 * §5.20.8). No run name, business date or other per-run value is ever a tag:
 *
 * <table>
 *   <caption>Meters</caption>
 *   <tr><td>{@value #NEXT_FIRE}</td><td>gauge, epoch seconds</td><td>job, trigger</td></tr>
 *   <tr><td>{@value #FIRES}</td><td>counter</td><td>job, trigger, outcome ({@code ran},
 *       {@code skipped-complete}, {@code skipped-duplicate}, {@code misfire-skipped}, {@code recovered},
 *       {@code failed-to-start})</td></tr>
 *   <tr><td>{@value #INSTANCES}</td><td>gauge</td><td>— ({@code quartz}: replicas checked in to the cluster)</td></tr>
 *   <tr><td>{@value #REPORT_FAILURES}</td><td>counter</td><td>— (schedule report writes that failed)</td></tr>
 *   <tr><td>{@value #CALENDAR_FAILURES}</td><td>counter</td><td>calendar ({@code quartz}: a refresh kept the previous version)</td></tr>
 *   <tr><td>{@value #CRON_DST_DIFFERENCES}</td><td>counter</td><td>job ({@code quartz}: an
 *       {@code adhar.batch.scheduler.jobs} cron whose Quartz conversion fires elsewhere at a clock
 *       change, one per agreement zone, counted at each conversion)</td></tr>
 * </table>
 *
 * <p>Without Micrometer on the classpath, or without a {@code MeterRegistry}, the engines record
 * into {@link #none()}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public interface SchedulerMetrics {

    /** Gauge: the next fire of a trigger, epoch seconds. */
    String NEXT_FIRE = "adhar.batch.scheduler.next.fire";

    /** Counter: fires by outcome. */
    String FIRES = "adhar.batch.scheduler.fires";

    /** Gauge: replicas checked in to the Quartz cluster. */
    String INSTANCES = "adhar.batch.scheduler.instances";

    /** Counter: schedule report writes that failed. */
    String REPORT_FAILURES = "adhar.batch.scheduler.report.failures";

    /** Counter: calendar refreshes that kept the previous version. */
    String CALENDAR_FAILURES = "adhar.batch.scheduler.calendar.failures";

    /** Counter: zones in which a converted Spring cron fires elsewhere at a clock change (R12). */
    String CRON_DST_DIFFERENCES = "adhar.batch.scheduler.cron.dst.differences";

    /**
     * @param job     the job's schedule name
     * @param trigger the trigger (a configured trigger's name, or a runtime trigger's kind)
     * @param outcome what the fire did
     */
    void fired(String job, String trigger, SlotOutcome outcome);

    /**
     * @param job     the job's schedule name
     * @param trigger the trigger
     * @param next    its next fire, {@code null} when it has none (the gauge is then removed)
     */
    void nextFire(String job, String trigger, Instant next);

    /**
     * @param count the replicas checked in to the cluster
     */
    void instances(int count);

    /**
     * Counts a schedule report write that failed.
     */
    void reportFailed();

    /**
     * @param calendar a calendar whose refresh kept its previous version
     */
    void calendarFailed(String calendar);

    /**
     * Counts one zone in which a job's {@code adhar.batch.scheduler.jobs} cron and its Quartz
     * conversion fire at different instants only because a clock change repeats or skips that local
     * time (design 0025 §5.20.6, R12). Records nothing unless implemented.
     *
     * @param job the job's schedule name
     */
    default void cronDstDifference(String job) {
    }

    /**
     * @param outcome what a fire did
     * @return its {@code outcome} tag
     */
    static String outcomeTag(SlotOutcome outcome) {
        return switch (outcome) {
            case RAN -> "ran";
            case ALREADY_RAN -> "skipped-complete";
            case DUPLICATE, OVERLAPPING, LOCKED_ELSEWHERE -> "skipped-duplicate";
            case MISFIRED, BEYOND_CATCH_UP -> "misfire-skipped";
            case RECOVERED -> "recovered";
            case FAILED_TO_START, ABANDONED -> "failed-to-start";
        };
    }

    /**
     * @return metrics that record nothing
     */
    static SchedulerMetrics none() {
        return None.INSTANCE;
    }

    /** Records nothing. */
    enum None implements SchedulerMetrics {
        INSTANCE;

        @Override
        public void fired(String job, String trigger, SlotOutcome outcome) {
        }

        @Override
        public void nextFire(String job, String trigger, Instant next) {
        }

        @Override
        public void instances(int count) {
        }

        @Override
        public void reportFailed() {
        }

        @Override
        public void calendarFailed(String calendar) {
        }
    }
}
