package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.calendar.BusinessCalendar;
import org.quartz.Calendar;
import org.quartz.impl.calendar.BaseCalendar;

import java.io.Serial;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * A design 0025 D5 business calendar as a Quartz {@link Calendar} (§5.20.6): a time is included
 * when its date, in the calendar's zone, is a {@code businessDays} entry, or is neither a
 * {@code weekendDays} day nor a {@code holidays} entry. Quartz's own calendars cannot say that: a
 * chain includes a time only when every calendar in it does, so a {@code HolidayCalendar} over a
 * {@code WeeklyCalendar} cannot bring back a working Saturday.
 *
 * <p>Quartz stores a calendar Java-serialised in {@code QRTZ_CALENDARS}, so the row is never the
 * source of truth (the kit re-registers every calendar at every start), and this class keeps a
 * fixed {@code serialVersionUID} and JDK-typed fields only: a row a replica of an earlier build
 * wrote still deserialises in a replica of this one.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class AdharBusinessCalendar extends BaseCalendar {

    @Serial
    private static final long serialVersionUID = 6_000_250_520_060_001L;

    /** The longest walk to the next business day: a year beyond any calendar's last listed date. */
    private static final int MAX_DAYS = 3 * 366;

    private String name;
    private String zone;
    /** The weekend, as {@link DayOfWeek#getValue()} numbers (1 = Monday). */
    private TreeSet<Integer> weekendDays;
    /** ISO dates. */
    private TreeSet<String> holidays;
    /** ISO dates. */
    private TreeSet<String> businessDays;

    /**
     * @param calendar the business calendar
     * @param zone     the zone its dates are taken in (the calendar's, or its triggers')
     */
    public AdharBusinessCalendar(BusinessCalendar calendar, ZoneId zone) {
        this.name = calendar.name();
        this.zone = zone.getId();
        this.weekendDays = new TreeSet<>();
        calendar.weekendDays().forEach(day -> weekendDays.add(day.getValue()));
        this.holidays = new TreeSet<>();
        calendar.holidays().forEach(date -> holidays.add(date.toString()));
        this.businessDays = new TreeSet<>();
        calendar.businessDays().forEach(date -> businessDays.add(date.toString()));
        setTimeZone(TimeZone.getTimeZone(zone));
        setDescription("Business calendar " + name + " in " + zone);
    }

    /**
     * @return the business calendar it holds, with its zone
     */
    public BusinessCalendar businessCalendar() {
        Set<DayOfWeek> weekend = EnumSet.noneOf(DayOfWeek.class);
        weekendDays.forEach(day -> weekend.add(DayOfWeek.of(day)));
        TreeSet<LocalDate> holidayDates = new TreeSet<>();
        holidays.forEach(date -> holidayDates.add(LocalDate.parse(date)));
        TreeSet<LocalDate> businessDates = new TreeSet<>();
        businessDays.forEach(date -> businessDates.add(LocalDate.parse(date)));
        return new BusinessCalendar(name, Optional.of(zone()), weekend, holidayDates, businessDates);
    }

    /**
     * @return the zone its dates are taken in
     */
    public ZoneId zone() {
        return ZoneId.of(zone);
    }

    @Override
    public boolean isTimeIncluded(long timeStamp) {
        return super.isTimeIncluded(timeStamp) && isBusinessDay(dateOf(timeStamp));
    }

    /**
     * @return the time itself when it is included, else the start of the next business day in the
     *         calendar's zone (Quartz's calendars answer the start of the day too), also included by
     *         the base calendar
     */
    @Override
    public long getNextIncludedTime(long timeStamp) {
        long candidate = timeStamp;
        for (int day = 0; day <= MAX_DAYS; day++) {
            long base = super.getNextIncludedTime(candidate);
            if (base > candidate) {
                candidate = base;
            }
            if (isTimeIncluded(candidate)) {
                return candidate;
            }
            candidate = dateOf(candidate).plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli();
        }
        throw new IllegalStateException("Business calendar " + name + " includes no time within " + MAX_DAYS
                + " days of " + Instant.ofEpochMilli(timeStamp));
    }

    private boolean isBusinessDay(LocalDate date) {
        String iso = date.toString();
        return businessDays.contains(iso)
                || !(weekendDays.contains(date.getDayOfWeek().getValue()) || holidays.contains(iso));
    }

    private LocalDate dateOf(long timeStamp) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), zone()).toLocalDate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object clone() {
        AdharBusinessCalendar copy = (AdharBusinessCalendar) super.clone();
        copy.weekendDays = (TreeSet<Integer>) weekendDays.clone();
        copy.holidays = (TreeSet<String>) holidays.clone();
        copy.businessDays = (TreeSet<String>) businessDays.clone();
        return copy;
    }
}
