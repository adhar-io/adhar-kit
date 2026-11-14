package com.adhar.kit.test.batch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes a job fail at a chosen item a chosen number of times. Declare it as a bean, call
 * {@link #check(Object)} with each item's key in the processor or (after the write, to prove the
 * write rolls back) in the writer, and let {@link RestartScenario} plan the failure.
 *
 * @since 0.1.0
 */
public class FailureInjector {

    private final Map<Object, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final AtomicInteger injected = new AtomicInteger();

    /**
     * @param item  the key of the item to fail on
     * @param times how many times to fail before letting it through
     */
    public void failOn(Object item, int times) {
        failures.put(item, new AtomicInteger(times));
    }

    /**
     * Forgets every planned failure.
     */
    public void clear() {
        failures.clear();
    }

    /**
     * @return how many failures were thrown
     */
    public int injected() {
        return injected.get();
    }

    /**
     * @param item the key of the item being handled
     * @throws InjectedFailure when a failure is planned for it
     */
    public void check(Object item) {
        AtomicInteger remaining = failures.get(item);
        if (remaining != null && remaining.getAndDecrement() > 0) {
            injected.incrementAndGet();
            throw new InjectedFailure("injected failure at item " + item);
        }
    }

    /**
     * The failure the injector throws: retryable as far as the kit's exit codes go (exit 5).
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
