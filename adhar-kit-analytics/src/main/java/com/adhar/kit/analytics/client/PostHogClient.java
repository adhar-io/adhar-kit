package com.adhar.kit.analytics.client;

import java.util.List;

/**
 * Abstraction over the PostHog HTTP API with correctly-routed, typed calls.
 *
 * <p>Replaces the legacy {@code sendEvent(String, Map)} helper that always
 * posted to {@code /capture/} regardless of the intended operation. Each
 * method here targets the real PostHog endpoint for that purpose:</p>
 * <ul>
 *   <li>{@link #capture(CaptureEvent)} - {@code POST /capture/} (single event)</li>
 *   <li>{@link #batch(List)} - {@code POST /batch/} (bulk events)</li>
 *   <li>{@link #decide(String)} - {@code POST /decide/?v=3} (feature flags)</li>
 * </ul>
 *
 * <p>PostHog itself implements identify/alias/group as specially-named
 * events ({@code $identify}, {@code $create_alias}, {@code $groupidentify})
 * delivered through capture/batch rather than as separate REST resources, so
 * callers build the correct {@link CaptureEvent} payload for those semantics
 * and send it through {@link #capture(CaptureEvent)} or {@link #batch(List)}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface PostHogClient {

    /**
     * Sends a single event to PostHog's {@code /capture/} endpoint.
     */
    void capture(CaptureEvent event);

    /**
     * Sends a batch of events to PostHog's {@code /batch/} endpoint in one HTTP call.
     */
    void batch(List<CaptureEvent> events);

    /**
     * Evaluates feature flags for a distinct id via PostHog's {@code /decide/} endpoint.
     */
    DecideResult decide(String distinctId);

    /**
     * Releases any underlying resources (connection pools, executors, ...).
     */
    default void close() {
    }
}
