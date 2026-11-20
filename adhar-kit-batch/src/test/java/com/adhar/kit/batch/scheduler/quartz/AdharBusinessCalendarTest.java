package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.calendar.BusinessCalendar;
import com.adhar.kit.batch.calendar.BusinessCalendarParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.impl.calendar.HolidayCalendar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.4 (design 0025 §5.20.6, R13): D5's rule as a Quartz calendar, and its stored form.
 */
class AdharBusinessCalendarTest {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    /** Christmas 2026 (a Friday) off, the Saturday after it worked. */
    private static final BusinessCalendar SG = BusinessCalendarParser.parse("""
            name: sg-business
            timeZone: Asia/Singapore
            holidays: [2026-12-25]
            businessDays: [2026-12-26]
            """, "sg.yaml");

    /** The BLOB a replica of the first build stored in QRTZ_CALENDARS (2026-11-20). */
    private static final String STORED_BLOB = "adhar-business-calendar-v1.ser.b64";

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    @Test
    @DisplayName("a time is included when its date in the calendar's zone is a business day: a holiday out, a worked Saturday in")
    void includesBusinessDaysInItsZone() {
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(SG, SINGAPORE);

        assertThat(calendar.isTimeIncluded(at("2026-12-24T09:00:00Z"))).as("Thursday").isTrue();
        assertThat(calendar.isTimeIncluded(at("2026-12-24T16:30:00Z"))).as("already Christmas in Singapore").isFalse();
        assertThat(calendar.isTimeIncluded(at("2026-12-25T16:30:00Z"))).as("the worked Saturday in Singapore").isTrue();
        assertThat(calendar.isTimeIncluded(at("2026-12-27T01:00:00Z"))).as("Sunday").isFalse();
        assertThat(calendar.zone()).isEqualTo(SINGAPORE);
        assertThat(calendar.getTimeZone().getID()).isEqualTo("Asia/Singapore");
        assertThat(calendar.businessCalendar()).as("round trip, with its zone").isEqualTo(new BusinessCalendar(
                SG.name(), java.util.Optional.of(SINGAPORE), SG.weekendDays(), SG.holidays(), SG.businessDays()));
    }

    @Test
    @DisplayName("the next included time is the time itself on a business day, else the start of the next one in the zone")
    void nextIncludedTime() {
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(SG, SINGAPORE);

        assertThat(calendar.getNextIncludedTime(at("2026-12-24T03:00:00Z"))).isEqualTo(at("2026-12-24T03:00:00Z"));
        assertThat(Instant.ofEpochMilli(calendar.getNextIncludedTime(at("2026-12-24T20:00:00Z"))))
                .as("Christmas in Singapore: the worked Saturday's midnight").isEqualTo(Instant.parse("2026-12-25T16:00:00Z"));
        assertThat(Instant.ofEpochMilli(calendar.getNextIncludedTime(at("2026-12-26T20:00:00Z"))))
                .as("Sunday: Monday's midnight").isEqualTo(Instant.parse("2026-12-27T16:00:00Z"));
    }

    @Test
    @DisplayName("a base calendar narrows it further: both must include a time")
    void honoursItsBaseCalendar() {
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(SG, SINGAPORE);
        HolidayCalendar closed = new HolidayCalendar();
        closed.setTimeZone(java.util.TimeZone.getTimeZone(SINGAPORE));
        closed.addExcludedDate(Date.from(LocalDate.of(2026, 12, 28).atStartOfDay(SINGAPORE).toInstant()));
        calendar.setBaseCalendar(closed);

        assertThat(calendar.isTimeIncluded(at("2026-12-28T02:00:00Z"))).as("Monday, closed by the base").isFalse();
        assertThat(Instant.ofEpochMilli(calendar.getNextIncludedTime(at("2026-12-26T20:00:00Z"))))
                .as("past Sunday and the base's closed Monday").isEqualTo(Instant.parse("2026-12-28T16:00:00Z"));
    }

    @Test
    @DisplayName("a calendar that includes no day within three years says so rather than looping")
    void aCalendarThatNeverIncludes() {
        BusinessCalendar everyDayOff = new BusinessCalendar("never", java.util.Optional.empty(),
                java.util.EnumSet.allOf(java.time.DayOfWeek.class), new java.util.TreeSet<>(), new java.util.TreeSet<>());
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(everyDayOff, ZoneId.of("UTC"));

        assertThatThrownBy(() -> calendar.getNextIncludedTime(at("2026-12-24T03:00:00Z")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("includes no time");
    }

    @Test
    @DisplayName("a clone is deep: changing one leaves the other")
    void clonesAreDeep() {
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(SG, SINGAPORE);
        AdharBusinessCalendar copy = (AdharBusinessCalendar) calendar.clone();

        assertThat(copy.businessCalendar()).isEqualTo(calendar.businessCalendar());
        assertThat(copy).isNotSameAs(calendar);
        copy.setBaseCalendar(new HolidayCalendar());
        assertThat(calendar.getBaseCalendar()).isNull();
    }

    @Test
    @DisplayName("R13: a calendar BLOB an earlier build stored still deserialises, under the fixed serialVersionUID")
    void aStoredBlobStillDeserialises() throws Exception {
        String encoded;
        try (InputStream in = getClass().getResourceAsStream(STORED_BLOB)) {
            assertThat(in).as("the stored BLOB fixture").isNotNull();
            encoded = new String(in.readAllBytes(), StandardCharsets.US_ASCII).strip();
        }
        Object stored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(encoded)))) {
            stored = in.readObject();
        }

        assertThat(stored).isInstanceOf(AdharBusinessCalendar.class);
        AdharBusinessCalendar calendar = (AdharBusinessCalendar) stored;
        assertThat(calendar.businessCalendar().name()).isEqualTo("sg-business");
        assertThat(calendar.isTimeIncluded(at("2026-12-24T16:30:00Z"))).as("Christmas in Singapore").isFalse();
        assertThat(calendar.isTimeIncluded(at("2026-12-25T16:30:00Z"))).as("the worked Saturday").isTrue();
        assertThat(ObjectStreamClass.lookup(AdharBusinessCalendar.class).getSerialVersionUID())
                .isEqualTo(6_000_250_520_060_001L);
    }

    @Test
    @DisplayName("what this build stores, this build reads back")
    void roundTripsThroughJavaSerialisation() throws Exception {
        AdharBusinessCalendar calendar = new AdharBusinessCalendar(SG, SINGAPORE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(calendar);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(((AdharBusinessCalendar) in.readObject()).businessCalendar()).isEqualTo(calendar.businessCalendar());
        }
    }
}
