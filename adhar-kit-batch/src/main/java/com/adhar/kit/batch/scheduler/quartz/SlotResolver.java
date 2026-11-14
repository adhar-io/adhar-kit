package com.adhar.kit.batch.scheduler.quartz;

import org.quartz.Calendar;
import org.quartz.JobDataMap;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Which slot a fire of a configured trigger stands for (design 0025 §5.20.5, {@code slotOf}): the
 * latest slot of the trigger's OWN grid at or before the fire's scheduled time, minus the
 * calendar's excluded times. A regular fire lies on the grid and comes back unchanged. The kit
 * never takes an identity from a time the misfire handling moved off the grid:
 *
 * <ul>
 *   <li>{@code MISFIRE_INSTRUCTION_FIRE_ONCE_NOW} ({@code cron}, {@code daily}) fires at the misfire's
 *       handling time; its slot is the newest one the outage missed.</li>
 *   <li>{@code MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT} ({@code interval})
 *       moves the {@code SimpleTrigger}'s {@code startTime} to the handling time, and every later
 *       fire with it. So an interval trigger's grid is never Quartz's {@code startTime}: it is
 *       {@value QuartzSchedulerEngine#GRID_START} + n x {@value QuartzSchedulerEngine#GRID_EVERY},
 *       written into the trigger's data when it is reconciled. Every 5 min handled at :07 runs as
 *       :05, and {@link #reanchored} puts the trigger back on :10.</li>
 *   <li>A recovery fire's scheduled time is the ORIGINAL fire's
 *       ({@code FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS}), taken on the
 *       original trigger's grid.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class SlotResolver {

    /** The widest look-back for a slot: a yearly cron expression still finds its last one. */
    static final Duration MAX_LOOK_BACK = Duration.ofDays(4 * 366L);

    /** The first look-back window, quadrupled until a slot is found. */
    static final Duration FIRST_LOOK_BACK = Duration.ofMinutes(1);

    /** Bounds a walk across excluded calendar times. */
    static final int MAX_STEPS = 100_000;

    private SlotResolver() {
    }

    /**
     * @param trigger   the configured trigger whose grid the slot is on (for a recovery fire, the
     *                  original trigger)
     * @param calendar  the trigger's calendar, {@code null} for none
     * @param scheduled the fire's scheduled time
     * @return the latest slot of the grid at or before it; the scheduled time, whole seconds, when
     *         the grid has none (a trigger that starts later)
     */
    public static Instant slotOf(Trigger trigger, Calendar calendar, Instant scheduled) {
        Interval grid = Interval.of(trigger.getJobDataMap());
        if (grid != null) {
            return grid.latestAtOrBefore(scheduled, calendar);
        }
        return latestFireAtOrBefore(trigger, calendar, scheduled);
    }

    /**
     * For misfire policy {@code all}: how many slots of the grid lie after this one, up to now.
     *
     * @param trigger  the configured trigger
     * @param calendar its calendar, {@code null} for none
     * @param slot     the slot a fire stands for
     * @param now      the current time
     * @param limit    counting stops here ({@code max-catch-up})
     * @return the newer slots, at most {@code limit}; {@code limit} or more means this slot is older
     *         than the {@code limit} most recent ones, and is skipped
     */
    public static int newerSlots(Trigger trigger, Calendar calendar, Instant slot, Instant now, int limit) {
        Interval grid = Interval.of(trigger.getJobDataMap());
        int count = 0;
        Instant cursor = slot;
        for (int steps = 0; count < limit && steps < MAX_STEPS; steps++) {
            cursor = grid != null ? grid.next(cursor) : next(trigger, cursor);
            if (cursor == null || cursor.isAfter(now)) {
                break;
            }
            if (included(calendar, cursor)) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param trigger an interval trigger
     * @return whether its {@code startTime} is still on the kit's grid; false after a
     *         {@code RESCHEDULE_NOW} misfire moved it to the handling time
     */
    public static boolean onGrid(Trigger trigger) {
        Interval grid = Interval.of(trigger.getJobDataMap());
        return grid == null || !(trigger instanceof SimpleTrigger) || grid.contains(trigger.getStartTime().toInstant());
    }

    /**
     * @param trigger an interval trigger
     * @param now     the current time
     * @return the slot an interval trigger re-anchored now first fires at: the first grid slot
     *         after now, so its following fires land on the grid again
     */
    public static Instant reanchored(Trigger trigger, Instant now) {
        Interval grid = Interval.of(trigger.getJobDataMap());
        if (grid == null) {
            throw new IllegalArgumentException("trigger " + trigger.getKey() + " has no interval grid");
        }
        return grid.next(now);
    }

    /**
     * @param start the grid's anchor
     * @param every its period
     * @param now   the current time
     * @return the first slot of {@code start + n x every} strictly after now
     */
    public static Instant firstSlotAfter(Instant start, Duration every, Instant now) {
        return new Interval(start, every).next(now);
    }

    private static Instant latestFireAtOrBefore(Trigger trigger, Calendar calendar, Instant scheduled) {
        Date target = Date.from(scheduled);
        Date exact = trigger.getFireTimeAfter(new Date(scheduled.toEpochMilli() - 1));
        if (exact != null && exact.equals(target) && included(calendar, scheduled)) {
            return scheduled;
        }
        for (Duration window = FIRST_LOOK_BACK; window.compareTo(MAX_LOOK_BACK) <= 0; window = window.multipliedBy(4)) {
            Date cursor = trigger.getFireTimeAfter(Date.from(scheduled.minus(window)));
            Date latest = null;
            for (int steps = 0; cursor != null && !cursor.after(target) && steps < MAX_STEPS; steps++) {
                if (included(calendar, cursor.toInstant())) {
                    latest = cursor;
                }
                cursor = trigger.getFireTimeAfter(cursor);
            }
            if (latest != null) {
                return latest.toInstant();
            }
        }
        return scheduled.truncatedTo(ChronoUnit.SECONDS);
    }

    private static Instant next(Trigger trigger, Instant after) {
        Date next = trigger.getFireTimeAfter(Date.from(after));
        return next == null ? null : next.toInstant();
    }

    private static boolean included(Calendar calendar, Instant time) {
        return calendar == null || calendar.isTimeIncluded(time.toEpochMilli());
    }

    /**
     * The kit's interval grid, {@code start + n x every}.
     *
     * @param start the anchor
     * @param every the period
     */
    record Interval(Instant start, Duration every) {

        Interval {
            if (every.isZero() || every.isNegative()) {
                throw new IllegalArgumentException("an interval grid needs a positive period, was " + every);
            }
        }

        static Interval of(JobDataMap data) {
            String every = data.getString(QuartzSchedulerEngine.GRID_EVERY);
            if (every == null) {
                return null;
            }
            return new Interval(Instant.parse(data.getString(QuartzSchedulerEngine.GRID_START)), Duration.parse(every));
        }

        Instant latestAtOrBefore(Instant time, Calendar calendar) {
            long period = every.toMillis();
            long offset = Math.floorDiv(time.toEpochMilli() - start.toEpochMilli(), period);
            Instant slot = start.plusMillis(offset * period);
            for (int steps = 0; !included(calendar, slot) && steps < MAX_STEPS; steps++) {
                slot = slot.minusMillis(period);
            }
            return slot;
        }

        Instant next(Instant after) {
            long period = every.toMillis();
            long offset = Math.floorDiv(after.toEpochMilli() - start.toEpochMilli(), period) + 1;
            return start.plusMillis(offset * period);
        }

        boolean contains(Instant time) {
            return Math.floorMod(time.toEpochMilli() - start.toEpochMilli(), every.toMillis()) == 0;
        }
    }
}
