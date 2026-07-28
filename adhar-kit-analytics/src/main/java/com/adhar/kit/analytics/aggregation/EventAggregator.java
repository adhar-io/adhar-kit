package com.adhar.kit.analytics.aggregation;

import com.adhar.kit.analytics.event.AnalyticsEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    // ==================== Funnel Analysis ====================

    /**
     * Computes an <em>ordered</em> conversion funnel over the supplied events.
     *
     * <p>For every distinct (non-null) {@code userId} the user's events are
     * sorted by timestamp and walked once, advancing through {@code orderedSteps}
     * strictly in order: a user "reaches" step <i>i</i> only after having
     * already reached steps <i>0..i-1</i> at earlier-or-equal timestamps. This
     * matches PostHog's ordered-funnel semantics (as opposed to a "any order"
     * funnel).</p>
     *
     * <p>The returned {@link FunnelStep#conversionRate()} is relative to the
     * number of users that entered the funnel (reached the first step), while
     * {@link FunnelStep#stepConversionRate()} is relative to the immediately
     * preceding step.</p>
     *
     * <p><b>Limits</b>: this is a purely in-memory computation over the events
     * you pass in - it does not query PostHog. It therefore only sees events
     * still retained in memory (e.g. a rolling buffer), events must carry a
     * non-null {@code eventType} and {@code timestamp}, and events without a
     * {@code userId} cannot be attributed to a user and are ignored. Cost is
     * O(E log E) per user for the sort.</p>
     *
     * @param events       the events to analyse (any order)
     * @param orderedSteps ordered list of event-type names defining the funnel
     * @return the per-step counts and conversion rates
     */
    public FunnelResult funnel(List<AnalyticsEvent> events, List<String> orderedSteps) {
        if (events == null || orderedSteps == null || orderedSteps.isEmpty()) {
            return new FunnelResult(List.of(), 0L);
        }

        Map<String, List<AnalyticsEvent>> byUser = events.stream()
                .filter(e -> e.getUserId() != null && e.getEventType() != null && e.getTimestamp() != null)
                .collect(Collectors.groupingBy(AnalyticsEvent::getUserId));

        long[] stepCounts = new long[orderedSteps.size()];
        for (List<AnalyticsEvent> userEvents : byUser.values()) {
            List<AnalyticsEvent> sorted = userEvents.stream()
                    .sorted(Comparator.comparing(AnalyticsEvent::getTimestamp))
                    .collect(Collectors.toList());
            int stepIndex = 0;
            for (AnalyticsEvent e : sorted) {
                if (stepIndex < orderedSteps.size() && orderedSteps.get(stepIndex).equals(e.getEventType())) {
                    stepIndex++;
                }
            }
            for (int i = 0; i < stepIndex; i++) {
                stepCounts[i]++;
            }
        }

        long entered = stepCounts[0];
        List<FunnelStep> steps = new ArrayList<>(orderedSteps.size());
        for (int i = 0; i < orderedSteps.size(); i++) {
            long count = stepCounts[i];
            double overall = entered == 0 ? 0.0 : (double) count / entered;
            double stepRate;
            if (i == 0) {
                stepRate = count == 0 ? 0.0 : 1.0;
            } else {
                long prev = stepCounts[i - 1];
                stepRate = prev == 0 ? 0.0 : (double) count / prev;
            }
            steps.add(new FunnelStep(orderedSteps.get(i), count, overall, stepRate));
        }
        return new FunnelResult(steps, entered);
    }

    /**
     * A single step of a computed {@link FunnelResult}.
     *
     * @param event              the event-type name for this step
     * @param count              users that reached this step (in order)
     * @param conversionRate     count relative to users that entered the funnel (step 0)
     * @param stepConversionRate count relative to the immediately preceding step
     */
    public record FunnelStep(String event, long count, double conversionRate, double stepConversionRate) {
    }

    /**
     * Result of a {@link #funnel(List, List)} computation.
     *
     * @param steps   per-step results, in funnel order
     * @param entered number of users that reached the first step
     */
    public record FunnelResult(List<FunnelStep> steps, long entered) {
        public FunnelResult {
            steps = steps != null ? List.copyOf(steps) : List.of();
        }
    }

    // ==================== Retention Analysis ====================

    /**
     * Computes a day-bucketed cohort retention matrix.
     *
     * <p>Each user is assigned to the cohort of the <em>calendar date of their
     * first</em> {@code cohortEvent}. For that cohort, day <i>d</i> retention
     * counts how many of its users performed {@code returnEvent} on
     * {@code cohortDate + d days}. Day 0 therefore measures users who did the
     * return event on the very day they entered the cohort.</p>
     *
     * <p><b>Limits</b>: in-memory only over the events supplied (no PostHog
     * query), so it only reflects events still retained in memory. Bucketing is
     * by {@link LocalDate} derived from each event's {@code timestamp} (system
     * calendar days, not rolling 24h windows). Events lacking a {@code userId},
     * {@code eventType} or {@code timestamp} are ignored, as are users that
     * never performed {@code cohortEvent}.</p>
     *
     * @param events       the events to analyse (any order)
     * @param cohortEvent  event-type name that places a user into a cohort
     * @param returnEvent  event-type name that counts as a "return"
     * @param dayBuckets   number of day buckets to compute (>= 1)
     * @return the retention matrix, cohorts ordered ascending by date
     */
    public RetentionMatrix retention(List<AnalyticsEvent> events, String cohortEvent,
                                     String returnEvent, int dayBuckets) {
        int buckets = Math.max(1, dayBuckets);
        if (events == null || cohortEvent == null || returnEvent == null) {
            return new RetentionMatrix(cohortEvent, returnEvent, buckets, List.of());
        }

        Map<String, LocalDate> firstCohort = new HashMap<>();
        Map<String, Set<LocalDate>> returnDates = new HashMap<>();
        for (AnalyticsEvent e : events) {
            if (e.getUserId() == null || e.getEventType() == null || e.getTimestamp() == null) {
                continue;
            }
            LocalDate date = e.getTimestamp().toLocalDate();
            if (cohortEvent.equals(e.getEventType())) {
                firstCohort.merge(e.getUserId(), date, (a, b) -> a.isBefore(b) ? a : b);
            }
            if (returnEvent.equals(e.getEventType())) {
                returnDates.computeIfAbsent(e.getUserId(), k -> new HashSet<>()).add(date);
            }
        }

        Map<LocalDate, List<String>> cohorts = new TreeMap<>();
        firstCohort.forEach((user, date) -> cohorts.computeIfAbsent(date, k -> new ArrayList<>()).add(user));

        List<RetentionRow> rows = new ArrayList<>(cohorts.size());
        for (Map.Entry<LocalDate, List<String>> entry : cohorts.entrySet()) {
            LocalDate cohortDate = entry.getKey();
            List<String> users = entry.getValue();
            List<Long> retained = new ArrayList<>(buckets);
            for (int d = 0; d < buckets; d++) {
                LocalDate target = cohortDate.plusDays(d);
                long cnt = users.stream().filter(u -> {
                    Set<LocalDate> rd = returnDates.get(u);
                    return rd != null && rd.contains(target);
                }).count();
                retained.add(cnt);
            }
            rows.add(new RetentionRow(cohortDate, users.size(), retained));
        }
        return new RetentionMatrix(cohortEvent, returnEvent, buckets, rows);
    }

    /**
     * One cohort row of a {@link RetentionMatrix}.
     *
     * @param cohortDate    the date users first performed the cohort event
     * @param cohortSize    number of users in this cohort
     * @param retainedByDay retained-user count for day 0, 1, ... (size == dayBuckets)
     */
    public record RetentionRow(LocalDate cohortDate, long cohortSize, List<Long> retainedByDay) {
        public RetentionRow {
            retainedByDay = retainedByDay != null ? List.copyOf(retainedByDay) : List.of();
        }
    }

    /**
     * Result of a {@link #retention(List, String, String, int)} computation.
     *
     * @param cohortEvent the cohort-defining event type
     * @param returnEvent the return-defining event type
     * @param dayBuckets  number of day buckets computed
     * @param rows        cohort rows, ordered ascending by cohort date
     */
    public record RetentionMatrix(String cohortEvent, String returnEvent, int dayBuckets, List<RetentionRow> rows) {
        public RetentionMatrix {
            rows = rows != null ? List.copyOf(rows) : List.of();
        }
    }
}

