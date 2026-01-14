package com.adhar.kit.analytics.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Framework-agnostic Analytics Service API.
 *
 * <p>This interface provides business analytics and event tracking capabilities
 * across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>Event tracking</li>
 *   <li>User behavior analytics</li>
 *   <li>Business metrics</li>
 *   <li>A/B testing support</li>
 *   <li>Funnel analysis</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * AnalyticsService analytics = AnalyticsFacade.getInstance();
 *
 * // Track events
 * analytics.track("user_signup", userId, Map.of(
 *     "plan", "premium",
 *     "source", "google"
 * ));
 *
 * // Track page views
 * analytics.trackPageView(userId, "/products", "Product Catalog");
 *
 * // Identify user
 * analytics.identify(userId, Map.of(
 *     "email", "user@example.com",
 *     "name", "John Doe"
 * ));
 *
 * // Get analytics
 * Map<String, Object> stats = analytics.getEventStats("user_signup",
 *     LocalDateTime.now().minusDays(7), LocalDateTime.now());
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.analytics.AnalyticsFacade
 */
public interface AnalyticsService {

    /**
     * Tracks an event.
     *
     * @param eventName the event name
     * @param userId the user ID
     * @param properties event properties
     */
    void track(String eventName, String userId, Map<String, Object> properties);

    /**
     * Tracks an event without user ID (anonymous).
     *
     * @param eventName the event name
     * @param properties event properties
     */
    void trackAnonymous(String eventName, Map<String, Object> properties);

    /**
     * Tracks a page view.
     *
     * @param userId the user ID
     * @param url the page URL
     * @param title the page title
     */
    void trackPageView(String userId, String url, String title);

    /**
     * Identifies a user with properties.
     *
     * @param userId the user ID
     * @param properties user properties
     */
    void identify(String userId, Map<String, Object> properties);

    /**
     * Records a business metric.
     *
     * @param metricName the metric name
     * @param value the metric value
     * @param tags optional tags
     */
    void recordMetric(String metricName, double value, Map<String, String> tags);

    /**
     * Gets event statistics for a time period.
     *
     * @param eventName the event name
     * @param start start time
     * @param end end time
     * @return event statistics
     */
    Map<String, Object> getEventStats(String eventName, LocalDateTime start, LocalDateTime end);

    /**
     * Gets user activity summary.
     *
     * @param userId the user ID
     * @param days number of days to look back
     * @return activity summary
     */
    Map<String, Object> getUserActivity(String userId, int days);

    /**
     * Creates a funnel for conversion tracking.
     *
     * @param funnelName the funnel name
     * @param steps funnel steps
     */
    void createFunnel(String funnelName, List<String> steps);

    /**
     * Gets funnel conversion data.
     *
     * @param funnelName the funnel name
     * @param start start time
     * @param end end time
     * @return funnel conversion stats
     */
    Map<String, Object> getFunnelStats(String funnelName, LocalDateTime start, LocalDateTime end);

    /**
     * Checks if analytics is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}

