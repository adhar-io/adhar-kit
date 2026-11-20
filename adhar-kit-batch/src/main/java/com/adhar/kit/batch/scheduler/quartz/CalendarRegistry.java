package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.calendar.BusinessCalendar;
import com.adhar.kit.batch.calendar.BusinessCalendarParser;
import com.adhar.kit.batch.calendar.BusinessCalendarParser.InvalidCalendarException;
import com.adhar.kit.batch.config.BatchProperties.Quartz.CalendarSettings;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The business calendars of the engine {@code quartz} (design 0025 §5.20.6): read from
 * {@code adhar.batch.quartz.calendars.<name>.*} (a mounted D5 {@code calendar.yaml}, or the same
 * keys as properties) and registered as {@link AdharBusinessCalendar}s.
 *
 * <ul>
 *   <li>At every start, each calendar is read and re-registered ({@code replace}, {@code updateTriggers}):
 *       the Java-serialised row in {@code QRTZ_CALENDARS} is never the source of truth. A calendar
 *       that cannot be read fails the start, naming it (FQ7).</li>
 *   <li>Every {@code calendars.refresh-interval}, the file-backed calendars are read again, because a
 *       mounted ConfigMap changes in place. A changed calendar is re-registered; one that cannot be
 *       read any more keeps the previous version, and the failure is logged and counted (FQ7).</li>
 *   <li>A calendar's dates are taken in its {@code time-zone}, else {@code adhar.batch.scheduler.time-zone};
 *       a trigger and its calendar must share that zone, because a business day is a date.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class CalendarRegistry {

    /** The properties the calendars are read from. */
    public static final String PREFIX = "adhar.batch.quartz.calendars";

    /** The one key under {@value #PREFIX} that is not a calendar. */
    static final String REFRESH_INTERVAL = "refresh-interval";

    private final Map<String, CalendarSettings> settings;
    private final ZoneId defaultZone;
    private final Map<String, BusinessCalendar> current = new ConcurrentHashMap<>();

    /**
     * @param settings    the calendars by name
     * @param defaultZone {@code adhar.batch.scheduler.time-zone}, for a calendar that names no zone
     */
    public CalendarRegistry(Map<String, CalendarSettings> settings, ZoneId defaultZone) {
        this.settings = Collections.unmodifiableMap(new TreeMap<>(settings));
        this.defaultZone = defaultZone;
    }

    /**
     * @return a registry of no calendars
     */
    public static CalendarRegistry none() {
        return new CalendarRegistry(Map.of(), ZoneId.of("UTC"));
    }

    /**
     * @param environment the application's environment
     * @return {@code adhar.batch.quartz.calendars.<name>.*} by name, {@value #REFRESH_INTERVAL} left out
     */
    public static Map<String, CalendarSettings> bind(Environment environment) {
        ConfigurationPropertyName prefix = ConfigurationPropertyName.of(PREFIX);
        Set<String> names = new TreeSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource iterable) {
                iterable.stream().filter(prefix::isAncestorOf)
                        .forEach(name -> names.add(name.getElement(prefix.getNumberOfElements(), Form.DASHED)));
            }
        }
        names.remove(REFRESH_INTERVAL);
        Binder binder = Binder.get(environment);
        Map<String, CalendarSettings> calendars = new LinkedHashMap<>();
        for (String name : names) {
            calendars.put(name, binder.bind(prefix.append(name), Bindable.of(CalendarSettings.class)).orElseGet(CalendarSettings::new));
        }
        return calendars;
    }

    /**
     * @return the configured calendars' names
     */
    public Set<String> names() {
        return settings.keySet();
    }

    /**
     * @param name a calendar's name
     * @return whether it is configured
     */
    public boolean contains(String name) {
        return settings.containsKey(name);
    }

    /**
     * @param name a configured calendar
     * @return the zone its dates are taken in
     */
    public ZoneId zone(String name) {
        return calendar(name).zoneOr(defaultZone);
    }

    /**
     * @param name a configured calendar
     * @return its current version, read once if it was not yet
     * @throws IllegalStateException when it cannot be read
     */
    public BusinessCalendar calendar(String name) {
        BusinessCalendar calendar = current.get(name);
        if (calendar == null) {
            calendar = load(name);
            current.put(name, calendar);
        }
        return calendar;
    }

    /**
     * Reads every calendar and registers it with Quartz, replacing the stored version and
     * recomputing the fire times of the triggers that use it.
     *
     * @param scheduler the Quartz scheduler
     * @throws IllegalStateException naming the first calendar that cannot be read
     */
    public void register(Scheduler scheduler) throws SchedulerException {
        for (String name : settings.keySet()) {
            BusinessCalendar calendar = load(name);
            current.put(name, calendar);
            scheduler.addCalendar(name, quartz(calendar), true, true);
            log.info("Registered business calendar {} ({}, zone {}, {} holiday(s), {} business day(s))", name,
                    source(name), zone(name), calendar.holidays().size(), calendar.businessDays().size());
        }
    }

    /**
     * Reads the file-backed calendars again (FQ7): a changed one is re-registered, one that cannot
     * be read keeps its previous version.
     *
     * @param scheduler the Quartz scheduler
     * @return what changed and what failed
     */
    public Refresh refresh(Scheduler scheduler) {
        List<String> changed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, CalendarSettings> entry : settings.entrySet()) {
            String name = entry.getKey();
            if (entry.getValue().getFile() == null) {
                continue;
            }
            BusinessCalendar read;
            try {
                read = load(name);
            } catch (IllegalStateException ex) {
                failed.add(name);
                log.warn("Keeping the previous version of business calendar {}: {}", name, ex.getMessage());
                continue;
            }
            if (read.equals(current.get(name))) {
                continue;
            }
            try {
                scheduler.addCalendar(name, quartz(read), true, true);
                current.put(name, read);
                changed.add(name);
                log.info("Business calendar {} changed in {}: re-registered", name, source(name));
            } catch (SchedulerException ex) {
                failed.add(name);
                log.warn("Could not re-register business calendar {}: {}", name, ex.getMessage());
            }
        }
        return new Refresh(changed, failed);
    }

    private AdharBusinessCalendar quartz(BusinessCalendar calendar) {
        return new AdharBusinessCalendar(calendar, calendar.zoneOr(defaultZone));
    }

    private BusinessCalendar load(String name) {
        CalendarSettings calendar = settings.get(name);
        String where = PREFIX + "." + name;
        try {
            BusinessCalendar parsed;
            if (calendar.getFile() != null) {
                parsed = BusinessCalendarParser.parse(Path.of(calendar.getFile()));
                if (calendar.getTimeZone() != null) {
                    throw new IllegalStateException(where + " sets both file and time-zone: the zone belongs in the "
                            + "file, as timeZone");
                }
            } else {
                Map<String, Object> keys = new LinkedHashMap<>();
                keys.put("name", name);
                keys.put("timeZone", calendar.getTimeZone());
                keys.put("weekendDays", calendar.getWeekendDays());
                keys.put("holidays", calendar.getHolidays());
                keys.put("businessDays", calendar.getBusinessDays());
                parsed = BusinessCalendarParser.parse(keys, where);
            }
            return parsed;
        } catch (InvalidCalendarException ex) {
            throw new IllegalStateException(where + ": " + ex.getMessage(), ex);
        }
    }

    private String source(String name) {
        return Optional.ofNullable(settings.get(name).getFile()).orElse("properties");
    }

    /**
     * What a refresh did.
     *
     * @param changed the calendars re-registered
     * @param failed  the calendars that kept their previous version
     */
    public record Refresh(List<String> changed, List<String> failed) {
    }
}
