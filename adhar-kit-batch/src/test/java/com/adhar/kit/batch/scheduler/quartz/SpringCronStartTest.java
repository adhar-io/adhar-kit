package com.adhar.kit.batch.scheduler.quartz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.5 (design 0025 §5.20.6): an {@code adhar.batch.scheduler.jobs} schedule whose Quartz
 * conversion disagrees with Spring's fire times fails the start, naming the slot; one added at run
 * time is the caller's error.
 */
class SpringCronStartTest extends QuartzEngineFixture {

    @Test
    @DisplayName("a divergent schedule in adhar.batch.scheduler.jobs fails the start, naming the slot and the zone")
    void aDivergentScheduleFailsTheStart() {
        properties.getScheduler().getJobs().put("cronLatest", "0 30 1 * * *");

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'0 30 1 * * *' converts to Quartz '0 30 1 * * ?'")
                .hasMessageContaining("disagree at slot").hasMessageContaining("America/New_York");
        engine = null;
    }

    @Test
    @DisplayName("scheduled at run time, the same schedule is refused to the caller and nothing is stored")
    void aDivergentScheduleAtRunTimeIsRefused() throws Exception {
        reconciled();

        assertThatThrownBy(() -> engine.scheduleJob("cronLatest", "0 30 1 * * *"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disagree at slot");
        assertThat(engine.listScheduledJobs()).isEmpty();
    }
}
