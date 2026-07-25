package com.adhar.kit.analytics.client;

import java.util.Map;

/**
 * Result of a PostHog {@code /decide/} feature-flag evaluation call.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public record DecideResult(Map<String, Object> featureFlags) {

    public DecideResult {
        featureFlags = featureFlags != null ? Map.copyOf(featureFlags) : Map.of();
    }

    public static DecideResult empty() {
        return new DecideResult(Map.of());
    }
}
