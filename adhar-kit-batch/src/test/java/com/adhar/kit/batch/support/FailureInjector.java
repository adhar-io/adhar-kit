package com.adhar.kit.batch.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes a test job fail at a chosen item a chosen number of times (the {@code RestartScenario}
 * of design 0025 §5.19).
 */
public class FailureInjector {

    private final Map<Long, AtomicInteger> failures = new ConcurrentHashMap<>();

    /**
     * @param item  the item to fail on
     * @param times how many times to fail before letting it through
     */
    public void failOn(long item, int times) {
        failures.put(item, new AtomicInteger(times));
    }

    /**
     * Forgets every planned failure.
     */
    public void clear() {
        failures.clear();
    }

    /**
     * @param item the item being handled
     * @throws InjectedFailure when a failure is planned for it
     */
    public void check(long item) {
        AtomicInteger remaining = failures.get(item);
        if (remaining != null && remaining.getAndDecrement() > 0) {
            throw new InjectedFailure("injected failure at item " + item);
        }
    }

    /**
     * The failure the injector throws.
     */
    public static class InjectedFailure extends IllegalStateException {

        /**
         * @param message what failed
         */
        public InjectedFailure(String message) {
            super(message);
        }
    }
}
