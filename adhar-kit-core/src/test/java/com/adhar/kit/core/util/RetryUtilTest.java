package com.adhar.kit.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilTest {

    @Test
    void executeReturnsResultOnFirstSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        }, 3, 1);

        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    void executeRetriesUntilSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        }, 5, 1);

        assertEquals("recovered", result);
        assertEquals(3, calls.get());
    }

    @Test
    void executeThrowsLastExceptionWhenAllAttemptsFail() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RetryUtil.execute(() -> {
                calls.incrementAndGet();
                throw new IllegalStateException("boom-" + calls.get());
            }, 2, 1));

        // initial attempt + 2 retries = 3 invocations
        assertEquals(3, calls.get());
        assertEquals("boom-3", ex.getMessage());
    }

    @Test
    void executeWithBackoffReturnsResultOnFirstSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.executeWithBackoff(() -> {
            calls.incrementAndGet();
            return "value";
        }, 3, 1, 2.0);

        assertEquals("value", result);
        assertEquals(1, calls.get());
    }

    @Test
    void executeWithBackoffRetriesAndMultipliesDelay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.executeWithBackoff(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "done";
        }, 5, 1, 2.0);

        assertEquals("done", result);
        assertEquals(3, calls.get());
    }

    @Test
    void executeWithBackoffThrowsLastExceptionWhenAllFail() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            RetryUtil.executeWithBackoff(() -> {
                calls.incrementAndGet();
                throw new RuntimeException("fail-" + calls.get());
            }, 2, 1, 2.0));

        assertEquals(3, calls.get());
        assertEquals("fail-3", ex.getMessage());
    }

    @Test
    void executeWithBackoffAndPredicateRetriesUntilSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.executeWithBackoff(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "done";
        }, 5, 1, 2.0, 10, e -> e instanceof IllegalStateException);

        assertEquals("done", result);
        assertEquals(3, calls.get());
    }

    @Test
    void executeWithBackoffAndPredicateAbortsOnNonRetryableException() {
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            RetryUtil.executeWithBackoff(() -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("permanent");
            }, 5, 1, 2.0, 10, e -> e instanceof IllegalStateException));

        // Non-retryable failures abort on the first attempt
        assertEquals(1, calls.get());
        assertEquals("permanent", ex.getMessage());
    }

    @Test
    void executeWithBackoffAndNullPredicateRetriesEverything() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = RetryUtil.executeWithBackoff(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "ok";
        }, 3, 1, 2.0, 10, null);

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void executeWithBackoffAndPredicateThrowsLastExceptionWhenExhausted() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RetryUtil.executeWithBackoff(() -> {
                calls.incrementAndGet();
                throw new IllegalStateException("boom-" + calls.get());
            }, 2, 1, 2.0, 5, e -> true));

        assertEquals(3, calls.get());
        assertEquals("boom-3", ex.getMessage());
    }

    @Test
    void executeWithBackoffCapsDelayAtMaxDelay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long start = System.currentTimeMillis();
        // 4 retries with initial delay 1ms, multiplier 100 -> uncapped would be
        // 1 + 100 + 10000 + ... ms; capped at 5ms it stays fast.
        String result = RetryUtil.executeWithBackoff(() -> {
            if (calls.incrementAndGet() < 5) {
                throw new RuntimeException("transient");
            }
            return "done";
        }, 5, 1, 100.0, 5, null);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("done", result);
        assertEquals(5, calls.get());
        assertTrue(elapsed < 2000, "delays must be capped at maxDelayMs but took " + elapsed + "ms");
    }

    @Test
    void retryPolicyBuilderUsesDefaults() {
        RetryUtil.RetryPolicy policy = RetryUtil.RetryPolicy.builder().build();
        assertNotNull(policy);
    }

    @Test
    void retryPolicyBuilderAppliesCustomValues() {
        RetryUtil.RetryPolicy policy = RetryUtil.RetryPolicy.builder()
            .maxRetries(7)
            .initialDelay(500)
            .maxDelay(10000)
            .backoffMultiplier(3.0)
            .build();

        assertNotNull(policy);
    }
}
