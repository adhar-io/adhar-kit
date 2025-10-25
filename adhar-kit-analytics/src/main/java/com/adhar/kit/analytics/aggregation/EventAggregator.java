package com.adhar.kit.analytics.aggregation;

import com.adhar.kit.analytics.event.AnalyticsEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating analytics events.
 * Provides real-time aggregation and metrics calculation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class EventAggregator {

    /**
     * Count events by type.
     */
    public Map<String, Long> countByEventType(List<AnalyticsEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(
                        AnalyticsEvent::getEventType,
                        Collectors.counting()
                ));
    }

    /**
     * Count events by user.
     */
    public Map<String, Long> countByUser(List<AnalyticsEvent> events) {
        return events.stream()
                .filter(e -> e.getUserId() != null)
                .collect(Collectors.groupingBy(
                        AnalyticsEvent::getUserId,
                        Collectors.counting()
                ));
    }

    /**
     * Count events by category.
     */
    public Map<String, Long> countByCategory(List<AnalyticsEvent> events) {
        return events.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        AnalyticsEvent::getCategory,
                        Collectors.counting()
                ));
    }

    /**
     * Get events within time range.
     */
    public List<AnalyticsEvent> filterByTimeRange(List<AnalyticsEvent> events,
                                                   LocalDateTime start,
                                                   LocalDateTime end) {
        return events.stream()
                .filter(e -> e.getTimestamp() != null)
                .filter(e -> !e.getTimestamp().isBefore(start) && !e.getTimestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * Get top users by event count.
     */
    public List<Map.Entry<String, Long>> getTopUsers(List<AnalyticsEvent> events, int limit) {
        return countByUser(events).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Calculate hourly event distribution.
     */
    public Map<Integer, Long> getHourlyDistribution(List<AnalyticsEvent> events) {
        return events.stream()
                .filter(e -> e.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getTimestamp().getHour(),
                        Collectors.counting()
                ));
    }

    /**
     * Get unique users count.
     */
    public long getUniqueUserCount(List<AnalyticsEvent> events) {
        return events.stream()
                .map(AnalyticsEvent::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /**
     * Get unique sessions count.
     */
    public long getUniqueSessionCount(List<AnalyticsEvent> events) {
        return events.stream()
                .map(AnalyticsEvent::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /**
     * Group events by time window (e.g., hourly, daily).
     */
    public Map<String, List<AnalyticsEvent>> groupByTimeWindow(List<AnalyticsEvent> events,
                                                                TimeWindow window) {
        return events.stream()
                .filter(e -> e.getTimestamp() != null)
                .collect(Collectors.groupingBy(e -> getTimeWindowKey(e.getTimestamp(), window)));
    }

    private String getTimeWindowKey(LocalDateTime timestamp, TimeWindow window) {
        return switch (window) {
            case HOURLY -> timestamp.toLocalDate() + " " + timestamp.getHour() + ":00";
            case DAILY -> timestamp.toLocalDate().toString();
            case WEEKLY -> timestamp.toLocalDate().minusDays(timestamp.getDayOfWeek().getValue() - 1).toString();
            case MONTHLY -> timestamp.getYear() + "-" + String.format("%02d", timestamp.getMonthValue());
        };
    }

    public enum TimeWindow {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY
    }
}

