package com.adhar.kit.analytics.aggregation;

import com.adhar.kit.analytics.event.AnalyticsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventAggregator Tests")
class EventAggregatorTest {

    private EventAggregator aggregator;
    private List<AnalyticsEvent> events;

    private AnalyticsEvent event(String type, String category, String user, String session, LocalDateTime ts) {
        return AnalyticsEvent.builder()
                .eventType(type)
                .category(category)
                .userId(user)
                .sessionId(session)
                .timestamp(ts)
                .build();
    }

    @BeforeEach
    void setUp() {
        aggregator = new EventAggregator();
        events = List.of(
                event("login", "user", "u1", "s1", LocalDateTime.of(2024, 1, 1, 9, 0)),
                event("login", "user", "u1", "s1", LocalDateTime.of(2024, 1, 1, 10, 0)),
                event("login", "user", "u2", "s2", LocalDateTime.of(2024, 1, 2, 10, 0)),
                event("logout", "user", "u2", "s3", LocalDateTime.of(2024, 1, 8, 11, 0)),
                event("purchase", "order", null, null, LocalDateTime.of(2024, 2, 15, 9, 0))
        );
    }

    @Test
    @DisplayName("countByEventType groups and counts")
    void countByEventType() {
        Map<String, Long> result = aggregator.countByEventType(events);
        assertEquals(3, result.get("login"));
        assertEquals(1, result.get("logout"));
        assertEquals(1, result.get("purchase"));
    }

    @Test
    @DisplayName("countByUser skips null users")
    void countByUser() {
        Map<String, Long> result = aggregator.countByUser(events);
        assertEquals(2, result.get("u1"));
        assertEquals(2, result.get("u2"));
        assertFalse(result.containsValue(null));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("countByCategory skips null categories")
    void countByCategory() {
        Map<String, Long> result = aggregator.countByCategory(events);
        assertEquals(4, result.get("user"));
        assertEquals(1, result.get("order"));
    }

    @Test
    @DisplayName("filterByTimeRange returns inclusive range")
    void filterByTimeRange() {
        List<AnalyticsEvent> result = aggregator.filterByTimeRange(events,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 1, 2, 23, 59));
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("getTopUsers returns sorted limited list")
    void getTopUsers() {
        List<Map.Entry<String, Long>> top = aggregator.getTopUsers(events, 1);
        assertEquals(1, top.size());
        assertEquals(2L, top.get(0).getValue());
    }

    @Test
    @DisplayName("getHourlyDistribution buckets by hour")
    void getHourlyDistribution() {
        Map<Integer, Long> dist = aggregator.getHourlyDistribution(events);
        assertEquals(2L, dist.get(9));
        assertEquals(2L, dist.get(10));
        assertEquals(1L, dist.get(11));
    }

    @Test
    @DisplayName("getUniqueUserCount counts distinct non-null")
    void getUniqueUserCount() {
        assertEquals(2L, aggregator.getUniqueUserCount(events));
    }

    @Test
    @DisplayName("getUniqueSessionCount counts distinct non-null")
    void getUniqueSessionCount() {
        assertEquals(3L, aggregator.getUniqueSessionCount(events));
    }

    @Test
    @DisplayName("groupByTimeWindow handles all windows")
    void groupByTimeWindow() {
        Map<String, List<AnalyticsEvent>> hourly = aggregator.groupByTimeWindow(events, EventAggregator.TimeWindow.HOURLY);
        assertFalse(hourly.isEmpty());
        assertTrue(hourly.containsKey("2024-01-01 9:00"));

        Map<String, List<AnalyticsEvent>> daily = aggregator.groupByTimeWindow(events, EventAggregator.TimeWindow.DAILY);
        assertTrue(daily.containsKey("2024-01-01"));

        Map<String, List<AnalyticsEvent>> weekly = aggregator.groupByTimeWindow(events, EventAggregator.TimeWindow.WEEKLY);
        // 2024-01-01 is a Monday -> week key is itself
        assertTrue(weekly.containsKey("2024-01-01"));

        Map<String, List<AnalyticsEvent>> monthly = aggregator.groupByTimeWindow(events, EventAggregator.TimeWindow.MONTHLY);
        assertTrue(monthly.containsKey("2024-01"));
        assertTrue(monthly.containsKey("2024-02"));
    }

    @Test
    @DisplayName("TimeWindow enum values resolvable")
    void timeWindowEnum() {
        assertEquals(4, EventAggregator.TimeWindow.values().length);
        assertEquals(EventAggregator.TimeWindow.DAILY, EventAggregator.TimeWindow.valueOf("DAILY"));
    }
}
