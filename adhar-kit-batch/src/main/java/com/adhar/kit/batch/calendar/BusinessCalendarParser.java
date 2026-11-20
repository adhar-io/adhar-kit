package com.adhar.kit.batch.calendar;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Reads a business calendar in design 0025 D5's {@code calendar.yaml} shape, the one parser of the
 * platform's run-pod calendar file and the in-service engine's calendars, whether mounted as that
 * file or written as properties with the same keys:
 *
 * <pre>
 * name: sg-business            # the calendar's name
 * timeZone: Asia/Singapore     # optional; the job's zone when unset
 * weekendDays: [SATURDAY, SUNDAY]   # default; [] for none
 * holidays: [2026-12-25]       # ISO dates that are not business days
 * businessDays: [2026-12-26]   # ISO dates that are, overriding the weekend
 * </pre>
 *
 * <p>An unknown key, a malformed date, zone or day, or a calendar that can never include a day is
 * an {@link InvalidCalendarException} naming the source and every problem: a misspelt
 * {@code holiday:} would otherwise be a calendar without its holidays, and nothing would say so.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class BusinessCalendarParser {

    /** The file's keys. */
    public static final Set<String> KEYS = Set.of("name", "timeZone", "weekendDays", "holidays", "businessDays");

    private BusinessCalendarParser() {
    }

    /**
     * @param file a {@code calendar.yaml}
     * @return the calendar it holds
     * @throws InvalidCalendarException when it cannot be read or is not a valid calendar
     */
    public static BusinessCalendar parse(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new InvalidCalendarException(file.toString(), List.of("cannot be read: " + ex.getMessage()), ex);
        }
        return parse(text, file.toString());
    }

    /**
     * @param yaml   a {@code calendar.yaml}'s text
     * @param source where it came from, for the messages
     * @return the calendar it holds
     * @throws InvalidCalendarException when it is not a valid calendar
     */
    public static BusinessCalendar parse(String yaml, String source) {
        Object document;
        try {
            LoaderOptions options = new LoaderOptions();
            document = new Yaml(new SafeConstructor(options), new Representer(new DumperOptions()), new DumperOptions(),
                    options, new DatesAsText()).load(yaml);
        } catch (YAMLException ex) {
            throw new InvalidCalendarException(source, List.of("is not YAML: " + firstLine(ex.getMessage())), ex);
        }
        if (!(document instanceof Map<?, ?> map)) {
            throw new InvalidCalendarException(source, List.of("is not a mapping of " + new TreeSet<>(KEYS)));
        }
        return parse(map, source);
    }

    /**
     * A calendar as a mapping of the file's keys: a parsed file, or properties written with them.
     *
     * @param map    {@code name}, {@code timeZone}, {@code weekendDays}, {@code holidays},
     *               {@code businessDays}; an absent {@code weekendDays} is D5's default
     * @param source where it came from, for the messages
     * @return the calendar
     * @throws InvalidCalendarException when it is not a valid calendar
     */
    public static BusinessCalendar parse(Map<?, ?> map, String source) {
        List<String> problems = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (!KEYS.contains(String.valueOf(key))) {
                problems.add("unknown key '" + key + "' (the keys are " + new TreeSet<>(KEYS) + ")");
            }
        }
        String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
        if (name == null || name.isBlank()) {
            problems.add("name is required");
        }
        return build(source, name, map.get("timeZone"), map.containsKey("weekendDays") && map.get("weekendDays") != null,
                map.get("weekendDays"), map.get("holidays"), map.get("businessDays"), problems);
    }

    private static final String NEVER_INCLUDES = "every day of the week is a weekend day and no businessDays "
            + "are listed, so no time is ever included";

    private static BusinessCalendar build(String source, String name, Object zoneValue, boolean weekendGiven,
                                          Object weekendValue, Object holidaysValue, Object businessDaysValue,
                                          List<String> problems) {
        ZoneId zone = null;
        if (zoneValue != null && !String.valueOf(zoneValue).isBlank()) {
            try {
                zone = ZoneId.of(String.valueOf(zoneValue).strip());
            } catch (DateTimeException ex) {
                problems.add("timeZone '" + zoneValue + "' is not a time zone (an IANA name such as Asia/Singapore)");
            }
        }
        Set<DayOfWeek> weekend = weekendGiven ? days(weekendValue, problems) : BusinessCalendar.DEFAULT_WEEKEND;
        SortedSet<LocalDate> holidays = dates("holidays", holidaysValue, problems);
        SortedSet<LocalDate> businessDays = dates("businessDays", businessDaysValue, problems);
        if (problems.isEmpty()) {
            BusinessCalendar calendar = new BusinessCalendar(name, Optional.ofNullable(zone), weekend, holidays,
                    businessDays);
            if (calendar.hasBusinessDays()) {
                return calendar;
            }
            problems.add(NEVER_INCLUDES);
        }
        throw new InvalidCalendarException(source, problems);
    }

    private static Set<DayOfWeek> days(Object value, List<String> problems) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Object day : list("weekendDays", value, problems)) {
            try {
                days.add(DayOfWeek.valueOf(String.valueOf(day).strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                problems.add("weekendDays: '" + day + "' is not a day of the week (MONDAY ... SUNDAY)");
            }
        }
        return days;
    }

    private static SortedSet<LocalDate> dates(String key, Object value, List<String> problems) {
        SortedSet<LocalDate> dates = new TreeSet<>();
        for (Object date : list(key, value, problems)) {
            try {
                dates.add(LocalDate.parse(String.valueOf(date).strip()));
            } catch (DateTimeParseException ex) {
                problems.add(key + ": '" + date + "' is not an ISO date (2026-12-25)");
            }
        }
        return dates;
    }

    private static List<?> list(String key, Object value, List<String> problems) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        problems.add(key + " must be a list");
        return List.of();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }

    /**
     * YAML 1.1 reads an unquoted {@code 2026-12-25} as a timestamp, and a {@code 2026-13-01} as a
     * date that rolls into the next year: dates stay text here, and {@link LocalDate#parse} judges them.
     */
    private static final class DatesAsText extends Resolver {

        @Override
        public void addImplicitResolver(Tag tag, Pattern regexp, String first, int limit) {
            if (!Tag.TIMESTAMP.equals(tag)) {
                super.addImplicitResolver(tag, regexp, first, limit);
            }
        }
    }

    /**
     * A calendar that cannot be used: the start fails with it, a refresh keeps the previous one, and
     * the Kubernetes runner exits 2.
     */
    public static class InvalidCalendarException extends IllegalArgumentException {

        private final String source;
        private final List<String> problems;

        InvalidCalendarException(String source, List<String> problems) {
            this(source, problems, null);
        }

        InvalidCalendarException(String source, List<String> problems, Throwable cause) {
            super("Business calendar " + source + " is invalid: " + String.join("; ", problems), cause);
            this.source = source;
            this.problems = List.copyOf(problems);
        }

        /**
         * @return where the calendar came from
         */
        public String getSource() {
            return source;
        }

        /**
         * @return every problem found
         */
        public List<String> getProblems() {
            return problems;
        }
    }
}
