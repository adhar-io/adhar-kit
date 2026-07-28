package com.adhar.kit.analytics.aggregation;

import com.adhar.kit.analytics.event.AnalyticsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventAggregator funnel & retention Tests")
class EventAggregatorFunnelRetentionTest {

    private final EventAggregator aggregator = new EventAggregator();

    private AnalyticsEvent event(String type, String user, LocalDateTime ts) {
        return AnalyticsEvent.builder().eventType(type).userId(user).timestamp(ts).build();
    }

    // ==================== Funnel ====================

    @Test
    @DisplayName("funnel counts users reaching each ordered step and computes rates")
    void funnelCountsAndRates() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 10, 0);
        List<AnalyticsEvent> events = List.of(
                // u1 completes all three steps in order
                event("view", "u1", base),
                event("signup", "u1", base.plusMinutes(1)),
                event("purchase", "u1", base.plusMinutes(2)),
                // u2 reaches step 2 only
                event("view", "u2", base),
                event("signup", "u2", base.plusMinutes(1)),
                // u3 reaches step 1 only
                event("view", "u3", base)
        );

        EventAggregator.FunnelResult result =
                aggregator.funnel(events, List.of("view", "signup", "purchase"));

        assertEquals(3, result.entered());
        assertEquals(3, result.steps().size());

        assertEquals(3, result.steps().get(0).count());
        assertEquals(2, result.steps().get(1).count());
        assertEquals(1, result.steps().get(2).count());

        // overall conversion relative to entered (3)
        assertEquals(1.0, result.steps().get(0).conversionRate(), 1e-9);
        assertEquals(2.0 / 3.0, result.steps().get(1).conversionRate(), 1e-9);
        assertEquals(1.0 / 3.0, result.steps().get(2).conversionRate(), 1e-9);

        // step-to-step conversion
        assertEquals(1.0, result.steps().get(0).stepConversionRate(), 1e-9);
        assertEquals(2.0 / 3.0, result.steps().get(1).stepConversionRate(), 1e-9);
        assertEquals(0.5, result.steps().get(2).stepConversionRate(), 1e-9);
    }

    @Test
    @DisplayName("funnel enforces step order - out-of-order events do not advance the funnel")
    void funnelEnforcesOrder() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 10, 0);
        // signup happens before view, so the user never completes step 2 (signup after view)
        List<AnalyticsEvent> events = List.of(
                event("signup", "u1", base),
                event("view", "u1", base.plusMinutes(1))
        );

        EventAggregator.FunnelResult result =
                aggregator.funnel(events, List.of("view", "signup"));

        assertEquals(1, result.steps().get(0).count());
        assertEquals(0, result.steps().get(1).count());
    }

    @Test
    @DisplayName("funnel ignores events without userId/eventType/timestamp")
    void funnelIgnoresIncompleteEvents() {
        List<AnalyticsEvent> events = List.of(
                event("view", null, LocalDateTime.now()),
                event(null, "u1", LocalDateTime.now()),
                AnalyticsEvent.builder().eventType("view").userId("u2").build() // no timestamp
        );
        EventAggregator.FunnelResult result = aggregator.funnel(events, List.of("view"));
        assertEquals(0, result.entered());
    }

    @Test
    @DisplayName("funnel with empty/null steps returns an empty result")
    void funnelEmptySteps() {
        assertEquals(0, aggregator.funnel(List.of(), List.of()).entered());
        assertTrue(aggregator.funnel(List.of(), null).steps().isEmpty());
        assertTrue(aggregator.funnel(null, List.of("view")).steps().isEmpty());
    }

    @Test
    @DisplayName("funnel with nobody entering yields zero rates, not NaN")
    void funnelNoEntrants() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 10, 0);
        EventAggregator.FunnelResult result =
                aggregator.funnel(List.of(event("other", "u1", base)), List.of("view", "signup"));
        assertEquals(0, result.entered());
        assertEquals(0.0, result.steps().get(0).conversionRate(), 1e-9);
        assertEquals(0.0, result.steps().get(0).stepConversionRate(), 1e-9);
        assertEquals(0.0, result.steps().get(1).stepConversionRate(), 1e-9);
    }

    // ==================== Retention ====================

    @Test
    @DisplayName("retention builds a cohort matrix keyed by first-cohort-event date")
    void retentionMatrix() {
        List<AnalyticsEvent> events = List.of(
                // Cohort date 2024-01-01: u1, u2
                event("signup", "u1", LocalDateTime.of(2024, 1, 1, 9, 0)),
                event("signup", "u2", LocalDateTime.of(2024, 1, 1, 12, 0)),
                // u1 returns day 0 and day 2; u2 returns day 1
                event("login", "u1", LocalDateTime.of(2024, 1, 1, 18, 0)),
                event("login", "u1", LocalDateTime.of(2024, 1, 3, 8, 0)),
                event("login", "u2", LocalDateTime.of(2024, 1, 2, 8, 0)),
                // Cohort date 2024-01-05: u3, returns day 0
                event("signup", "u3", LocalDateTime.of(2024, 1, 5, 9, 0)),
                event("login", "u3", LocalDateTime.of(2024, 1, 5, 10, 0))
        );

        EventAggregator.RetentionMatrix matrix =
                aggregator.retention(events, "signup", "login", 3);

        assertEquals("signup", matrix.cohortEvent());
        assertEquals("login", matrix.returnEvent());
        assertEquals(3, matrix.dayBuckets());
        assertEquals(2, matrix.rows().size());

        EventAggregator.RetentionRow row0 = matrix.rows().get(0);
        assertEquals(LocalDate.of(2024, 1, 1), row0.cohortDate());
        assertEquals(2, row0.cohortSize());
        // day0: u1 only ; day1: u2 only ; day2: u1 only
        assertEquals(List.of(1L, 1L, 1L), row0.retainedByDay());

        EventAggregator.RetentionRow row1 = matrix.rows().get(1);
        assertEquals(LocalDate.of(2024, 1, 5), row1.cohortDate());
        assertEquals(1, row1.cohortSize());
        assertEquals(List.of(1L, 0L, 0L), row1.retainedByDay());
    }

    @Test
    @DisplayName("retention assigns a user to the cohort of their earliest cohort event")
    void retentionUsesEarliestCohortDate() {
        List<AnalyticsEvent> events = List.of(
                event("signup", "u1", LocalDateTime.of(2024, 1, 3, 9, 0)),
                event("signup", "u1", LocalDateTime.of(2024, 1, 1, 9, 0)),
                event("login", "u1", LocalDateTime.of(2024, 1, 2, 9, 0))
        );
        EventAggregator.RetentionMatrix matrix = aggregator.retention(events, "signup", "login", 3);
        assertEquals(1, matrix.rows().size());
        assertEquals(LocalDate.of(2024, 1, 1), matrix.rows().get(0).cohortDate());
        // login on 2024-01-02 == day 1
        assertEquals(List.of(0L, 1L, 0L), matrix.rows().get(0).retainedByDay());
    }

    @Test
    @DisplayName("retention clamps dayBuckets to at least 1 and handles null events")
    void retentionEdgeCases() {
        EventAggregator.RetentionMatrix clamped =
                aggregator.retention(List.of(event("signup", "u1", LocalDateTime.of(2024, 1, 1, 9, 0))),
                        "signup", "login", 0);
        assertEquals(1, clamped.dayBuckets());
        assertEquals(1, clamped.rows().size());
        assertEquals(List.of(0L), clamped.rows().get(0).retainedByDay());

        assertTrue(aggregator.retention(null, "signup", "login", 2).rows().isEmpty());
        assertTrue(aggregator.retention(List.of(), null, "login", 2).rows().isEmpty());
    }

    @Test
    @DisplayName("retention ignores users who never performed the cohort event")
    void retentionIgnoresNonCohortUsers() {
        List<AnalyticsEvent> events = List.of(
                event("login", "u1", LocalDateTime.of(2024, 1, 1, 9, 0))
        );
        EventAggregator.RetentionMatrix matrix = aggregator.retention(events, "signup", "login", 2);
        assertTrue(matrix.rows().isEmpty());
    }
}
