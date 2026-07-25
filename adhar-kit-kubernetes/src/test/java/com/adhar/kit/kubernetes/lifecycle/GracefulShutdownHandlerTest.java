package com.adhar.kit.kubernetes.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GracefulShutdownHandler}. Uses an injected {@code Sleeper} so
 * tests never actually sleep for the configured drain period.
 */
class GracefulShutdownHandlerTest {

    private final List<Long> sleeps = new ArrayList<>();
    private final GracefulShutdownHandler.Sleeper recordingSleeper = millis -> sleeps.add(millis);

    @Test
    void startOpensReadinessGate() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofSeconds(5), recordingSleeper);

        assertFalse(handler.isReady());
        handler.start();

        assertTrue(handler.isReady());
        assertTrue(handler.isRunning());
    }

    @Test
    void beginShutdownFlipsReadinessAndSleepsForDrainPeriod() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofSeconds(5), recordingSleeper);
        handler.start();

        handler.beginShutdown();

        assertFalse(handler.isReady());
        assertFalse(handler.isRunning());
        assertEquals(1, sleeps.size());
        assertEquals(5000L, sleeps.get(0));
    }

    @Test
    void beginShutdownIsIdempotent() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofSeconds(5), recordingSleeper);
        handler.start();

        handler.beginShutdown();
        handler.beginShutdown();

        assertEquals(1, sleeps.size());
    }

    @Test
    void zeroDrainPeriodSkipsSleep() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ZERO, recordingSleeper);
        handler.start();

        handler.beginShutdown();

        assertTrue(sleeps.isEmpty());
    }

    @Test
    void nullDrainPeriodTreatedAsZero() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(null, recordingSleeper);
        handler.start();

        handler.beginShutdown();

        assertTrue(sleeps.isEmpty());
    }

    @Test
    void negativeDrainPeriodSkipsSleep() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofSeconds(-1), recordingSleeper);
        handler.start();

        handler.beginShutdown();

        assertTrue(sleeps.isEmpty());
    }

    @Test
    void interruptedSleepRestoresInterruptFlagAndDoesNotThrow() {
        GracefulShutdownHandler.Sleeper interruptingSleeper = millis -> {
            throw new InterruptedException("interrupted");
        };
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofSeconds(1), interruptingSleeper);
        handler.start();

        handler.beginShutdown();

        assertTrue(Thread.interrupted()); // also clears the flag for subsequent tests
    }

    @Test
    void stopRunnableCallbackDelegatesToBeginShutdownThenRunsCallback() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ZERO, recordingSleeper);
        handler.start();
        AtomicInteger callbackCalls = new AtomicInteger();

        handler.stop(callbackCalls::incrementAndGet);

        assertFalse(handler.isReady());
        assertEquals(1, callbackCalls.get());
    }

    @Test
    void noArgStopAlsoBeginsShutdown() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ZERO, recordingSleeper);
        handler.start();

        handler.stop();

        assertFalse(handler.isReady());
        assertFalse(handler.isRunning());
    }

    @Test
    void isAutoStartupAndPhaseAreSane() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ZERO, recordingSleeper);
        assertTrue(handler.isAutoStartup());
        assertEquals(Integer.MAX_VALUE, handler.getPhase());
    }

    @Test
    void defaultConstructorUsesRealThreadSleep() {
        // Exercises the public single-arg constructor (real Thread::sleep), with a
        // near-zero drain period so the test stays fast.
        GracefulShutdownHandler handler = new GracefulShutdownHandler(Duration.ofMillis(1));
        handler.start();

        handler.beginShutdown();

        assertFalse(handler.isReady());
    }
}
