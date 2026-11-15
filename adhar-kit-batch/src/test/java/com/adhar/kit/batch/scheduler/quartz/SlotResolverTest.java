package com.adhar.kit.batch.scheduler.quartz;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotResolverTest {

    @Test
    void slotOfUsesTheLatestGridSlotBeforeTheScheduledTime() {
        Trigger trigger = intervalTrigger();

        Instant scheduled = Instant.parse("2025-01-01T00:12:30Z");

        assertThat(SlotResolver.slotOf(trigger, null, scheduled))
                .isEqualTo(Instant.parse("2025-01-01T00:10:00Z"));
    }

    @Test
    void newerSlotsCountsOnlyTheMoreRecentSlotsWithinTheLimit() {
        Trigger trigger = intervalTrigger();
        Instant slot = Instant.parse("2025-01-01T00:00:00Z");
        Instant now = Instant.parse("2025-01-01T00:22:00Z");

        assertThat(SlotResolver.newerSlots(trigger, null, slot, now, 10))
                .isEqualTo(4);
    }

    @Test
    void onGridDetectsWhenTheTriggerStartNoLongerMatchesItsIntervalGrid() {
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("off-grid")
                .startAt(Date.from(Instant.parse("2025-01-01T00:02:00Z")))
                .withSchedule(org.quartz.SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(Duration.ofMinutes(5).toMillis())
                        .repeatForever())
                .usingJobData(new JobDataMap(Map.of(
                        QuartzSchedulerEngine.GRID_START, "2025-01-01T00:00:00Z",
                        QuartzSchedulerEngine.GRID_EVERY, Duration.ofMinutes(5).toString())))
                .build();

        assertThat(SlotResolver.onGrid(trigger)).isFalse();
    }

    @Test
    void reanchoredAndFirstSlotAfterKeepTheTriggerOnTheConfiguredGrid() {
        Trigger trigger = intervalTrigger();
        Instant now = Instant.parse("2025-01-01T00:02:30Z");

        assertThat(SlotResolver.reanchored(trigger, now))
                .isEqualTo(Instant.parse("2025-01-01T00:05:00Z"));
        assertThat(SlotResolver.firstSlotAfter(
                Instant.parse("2025-01-01T00:00:00Z"), Duration.ofMinutes(5), Instant.parse("2025-01-01T00:07:00Z")))
                .isEqualTo(Instant.parse("2025-01-01T00:10:00Z"));
    }

    @Test
    void intervalGridRejectsZeroOrNegativeDurations() {
        assertThatThrownBy(() -> new SlotResolver.Interval(Instant.parse("2025-01-01T00:00:00Z"), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive period");
    }

    private static Trigger intervalTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity("grid")
                .startAt(Date.from(Instant.parse("2025-01-01T00:00:00Z")))
                .withSchedule(org.quartz.SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(Duration.ofMinutes(5).toMillis())
                        .repeatForever())
                .usingJobData(new JobDataMap(Map.of(
                        QuartzSchedulerEngine.GRID_START, "2025-01-01T00:00:00Z",
                        QuartzSchedulerEngine.GRID_EVERY, Duration.ofMinutes(5).toString())))
                .build();
    }
}
