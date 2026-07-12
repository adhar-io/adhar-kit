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
