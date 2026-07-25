package com.adhar.kit.analytics.consent;

/**
 * SPI for a pluggable opt-out store. Implement this to back consent state
 * with a database, cache, or remote consent-management service instead of
 * the in-memory default.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface ConsentStore {

    /**
     * @return true if the given distinct id has opted out of analytics tracking.
     */
    boolean isOptedOut(String distinctId);

    /**
     * Records that the given distinct id has opted out.
     */
    void optOut(String distinctId);

    /**
     * Reverses a prior opt-out.
     */
    void optIn(String distinctId);
}
