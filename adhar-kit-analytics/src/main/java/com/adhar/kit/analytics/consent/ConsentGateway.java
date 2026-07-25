package com.adhar.kit.analytics.consent;

import lombok.extern.slf4j.Slf4j;

/**
 * Consulted before any event, identify, alias or group call leaves the
 * process, so that a per-distinct-id opt-out is honored before data ever
 * reaches PostHog.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ConsentGateway {

    private final ConsentStore store;

    public ConsentGateway(ConsentStore store) {
        this.store = store != null ? store : new InMemoryConsentStore();
    }

    /**
     * @return true if the distinct id may be sent to PostHog. A {@code null}
     * distinct id is always allowed here; callers are expected to validate
     * user-id presence separately.
     */
    public boolean isAllowed(String distinctId) {
        if (distinctId == null) {
            return true;
        }
        boolean optedOut = store.isOptedOut(distinctId);
        if (optedOut) {
            log.debug("Distinct id '{}' has opted out of analytics; suppressing send", distinctId);
        }
        return !optedOut;
    }

    public void optOut(String distinctId) {
        store.optOut(distinctId);
    }

    public void optIn(String distinctId) {
        store.optIn(distinctId);
    }

    public ConsentStore store() {
        return store;
    }
}
