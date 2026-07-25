package com.adhar.kit.analytics.consent;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default, in-memory {@link ConsentStore} seeded from configuration
 * (e.g. {@code adhar.analytics.consent.opted-out-ids}). Suitable for simple
 * deployments; swap in a persistent {@link ConsentStore} for durable,
 * cross-instance consent state.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class InMemoryConsentStore implements ConsentStore {

    private final Set<String> optedOut = ConcurrentHashMap.newKeySet();

    public InMemoryConsentStore() {
    }

    public InMemoryConsentStore(Collection<String> initiallyOptedOut) {
        if (initiallyOptedOut != null) {
            optedOut.addAll(initiallyOptedOut);
        }
    }

    @Override
    public boolean isOptedOut(String distinctId) {
        return distinctId != null && optedOut.contains(distinctId);
    }

    @Override
    public void optOut(String distinctId) {
        if (distinctId != null) {
            optedOut.add(distinctId);
        }
    }

    @Override
    public void optIn(String distinctId) {
        if (distinctId != null) {
            optedOut.remove(distinctId);
        }
    }

    public int size() {
        return optedOut.size();
    }
}
