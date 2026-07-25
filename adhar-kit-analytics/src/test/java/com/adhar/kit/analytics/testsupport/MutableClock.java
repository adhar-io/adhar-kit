package com.adhar.kit.analytics.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only {@link Clock} whose "now" can be advanced deterministically,
 * used to exercise TTL/expiry logic without sleeping in tests.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneOffset.UTC);
    }

    private MutableClock(Instant initial, ZoneId zone) {
        this.now = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public void advance(java.time.Duration by) {
        now.updateAndGet(i -> i.plus(by));
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now.get(), zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
