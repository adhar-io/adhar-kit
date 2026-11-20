package com.adhar.kit.batch.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A business calendar (design 0025 D5): which dates are business days. The same shape serves the
 * platform path, where the composition mounts the calendar into every run
 * ({@code ADHAR_BATCH_CALENDAR}) and a run on a non-business day is skipped, and the in-service
 * Quartz engine, where an excluded day never fires (§5.20.6).
 *
 * <p>A date is a business day when it is listed in {@code businessDays}, or is neither one of the
 * {@code weekendDays} nor listed in {@code holidays}. So a working Saturday is a
 * {@code businessDays} entry, and it wins over a {@code holidays} entry of the same date.</p>
 *
 * @param name         the calendar's name
 * @param timeZone     the zone its dates are taken in; empty for the job's (or trigger's) zone
 * @param weekendDays  the days of the week that are not business days
 * @param holidays     dates that are not business days
 * @param businessDays dates that ARE business days, whatever the rest says
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record BusinessCalendar(String name, Optional<ZoneId> timeZone, Set<DayOfWeek> weekendDays,
                               SortedSet<LocalDate> holidays, SortedSet<LocalDate> businessDays) {

    /** D5's default weekend. */
    public static final Set<DayOfWeek> DEFAULT_WEEKEND = Collections.unmodifiableSet(
            EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

    public BusinessCalendar {
        weekendDays = weekendDays.isEmpty() ? Collections.unmodifiableSet(EnumSet.noneOf(DayOfWeek.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(weekendDays));
        holidays = Collections.unmodifiableSortedSet(new TreeSet<>(holidays));
        businessDays = Collections.unmodifiableSortedSet(new TreeSet<>(businessDays));
    }

    /**
     * @param date a date
     * @return whether it is a business day
     */
    public boolean isBusinessDay(LocalDate date) {
        return businessDays.contains(date) || !(weekendDays.contains(date.getDayOfWeek()) || holidays.contains(date));
    }

    /**
     * @param instant     an instant
     * @param defaultZone the zone to take its date in when the calendar names none
     * @return whether its date, in the calendar's zone, is a business day
     */
    public boolean includes(Instant instant, ZoneId defaultZone) {
        return isBusinessDay(instant.atZone(zoneOr(defaultZone)).toLocalDate());
    }

    /**
     * @param defaultZone the job's or trigger's zone
     * @return the calendar's zone, else that one
     */
    public ZoneId zoneOr(ZoneId defaultZone) {
        return timeZone.orElse(defaultZone);
    }

    /**
     * @return whether some date can be a business day: a calendar whose weekend is the whole week
     *         and that lists no business day never includes a time
     */
    public boolean hasBusinessDays() {
        return weekendDays.size() < DayOfWeek.values().length || !businessDays.isEmpty();
    }
}
