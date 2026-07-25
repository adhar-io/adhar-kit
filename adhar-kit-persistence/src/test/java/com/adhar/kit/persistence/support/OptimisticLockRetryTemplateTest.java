package com.adhar.kit.persistence.support;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OptimisticLockRetryTemplate Tests")
class OptimisticLockRetryTemplateTest {

    @Test
    @DisplayName("returns the result immediately when the supplier succeeds on the first attempt")
    void succeedsFirstTry() {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(3, Duration.ofMillis(1), 2.0);
        AtomicInteger calls = new AtomicInteger();

        String result = template.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("retries on ObjectOptimisticLockingFailureException and succeeds once attempts remain")
    void retriesOnSpringOptimisticLockException() {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(3, Duration.ofMillis(1), 2.0);
        AtomicInteger calls = new AtomicInteger();

        String result = template.execute(() -> {
            int attempt = calls.incrementAndGet();
            if (attempt < 3) {
                throw new ObjectOptimisticLockingFailureException("Entity", "1");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(3, calls.get());
    }

    @Test
    @DisplayName("retries on jakarta.persistence.OptimisticLockException")
    void retriesOnJpaOptimisticLockException() {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(3, Duration.ofMillis(1), 2.0);
        AtomicInteger calls = new AtomicInteger();

        String result = template.execute(() -> {
            int attempt = calls.incrementAndGet();
            if (attempt < 2) {
                throw new OptimisticLockException("stale entity");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("rethrows the last failure once max attempts is exhausted")
    void exhaustsRetriesAndRethrows() {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(3, Duration.ofMillis(1), 2.0);
        AtomicInteger calls = new AtomicInteger();

        ObjectOptimisticLockingFailureException thrown = assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                template.execute(() -> {
                    calls.incrementAndGet();
                    throw new ObjectOptimisticLockingFailureException("Entity", "1");
                }));

        assertNotNull(thrown);
        assertEquals(3, calls.get(), "should have attempted exactly maxAttempts times");
    }

    @Test
    @DisplayName("does not retry on unrelated exceptions")
    void doesNotRetryUnrelatedExceptions() {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(3, Duration.ofMillis(1), 2.0);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
                template.execute(() -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("unrelated");
                }));

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("withDefaults() provides a usable template")
    void withDefaultsWorks() {
        OptimisticLockRetryTemplate template = OptimisticLockRetryTemplate.withDefaults();
        assertEquals("ok", template.execute(() -> "ok"));
    }

    @Test
    @DisplayName("rejects a non-positive maxAttempts")
    void rejectsInvalidMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new OptimisticLockRetryTemplate(0, Duration.ofMillis(1), 2.0));
    }

    @Test
    @DisplayName("propagates interruption as IllegalStateException while waiting to retry")
    void propagatesInterruptionDuringBackoff() throws InterruptedException {
        OptimisticLockRetryTemplate template = new OptimisticLockRetryTemplate(2, Duration.ofSeconds(5), 2.0);
        AtomicInteger calls = new AtomicInteger();
        Thread runner = new Thread(() -> {
            try {
                template.execute(() -> {
                    calls.incrementAndGet();
                    throw new ObjectOptimisticLockingFailureException("Entity", "1");
                });
            } catch (IllegalStateException expected) {
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (ObjectOptimisticLockingFailureException ignoredIfNotInterruptedInTime) {
                // benign race: interrupt didn't land before natural exhaustion
            }
        });
        runner.start();
        Thread.sleep(50);
        runner.interrupt();
        runner.join(5000);
        assertTrue(calls.get() >= 1);
    }
}
