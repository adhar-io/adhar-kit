package com.adhar.kit.analytics.client;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single, typed PostHog event ready to be sent via either the
 * {@code /capture/} (single) or {@code /batch/} (bulk) endpoint.
 *
 * <p>Instances are used uniformly for plain {@code track()} calls as well as
 * for the specially-named PostHog events that implement identify
 * ({@code $identify}), alias ({@code $create_alias}) and group
 * ({@code $groupidentify}) semantics - matching PostHog's actual HTTP API,
 * which does not expose separate REST endpoints for those operations.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public record CaptureEvent(String event, String distinctId, Map<String, Object> properties, Instant timestamp) {

    public CaptureEvent {
        properties = properties != null ? Collections.unmodifiableMap(new LinkedHashMap<>(properties)) : Map.of();
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Convenience factory that stamps the current time.
     */
    public static CaptureEvent of(String event, String distinctId, Map<String, Object> properties) {
        return new CaptureEvent(event, distinctId, properties, Instant.now());
    }
}
