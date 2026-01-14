package com.adhar.kit.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CoreFacade.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("CoreFacade Tests")
class CoreFacadeTest {

    private final CoreFacade core = CoreFacade.getInstance();

    @Test
    @DisplayName("Should return singleton instance")
    void testSingleton() {
        CoreFacade instance1 = CoreFacade.getInstance();
        CoreFacade instance2 = CoreFacade.getInstance();

        assertSame(instance1, instance2, "Should return same instance");
    }

    // ==================== ID Generation Tests ====================

    @Test
    @DisplayName("Should generate unique IDs")
    void testGenerateId() {
        String id1 = core.generateId();
        String id2 = core.generateId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertEquals(32, id1.length(), "ID should be 32 characters");
        assertFalse(id1.contains("-"), "ID should not contain hyphens");
    }

    @Test
    @DisplayName("Should generate UUID")
    void testGenerateUUID() {
        String uuid1 = core.generateUUID();
        String uuid2 = core.generateUUID();

        assertNotNull(uuid1);
        assertNotNull(uuid2);
        assertNotEquals(uuid1, uuid2);
        assertEquals(36, uuid1.length(), "UUID should be 36 characters");
        assertTrue(uuid1.contains("-"), "UUID should contain hyphens");
    }

    @Test
    @DisplayName("Should generate short ID")
    void testGenerateShortId() {
        String shortId = core.generateShortId();

        assertNotNull(shortId);
        assertEquals(8, shortId.length(), "Short ID should be 8 characters");
    }

    @Test
    @DisplayName("Should generate snowflake ID")
    void testGenerateSnowflakeId() {
        long id1 = core.generateSnowflakeId();
        long id2 = core.generateSnowflakeId();

        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
        assertNotEquals(id1, id2);
    }

    // ==================== JSON Tests ====================

    @Test
    @DisplayName("Should serialize object to JSON")
    void testToJson() {
        TestData data = new TestData("test", 123);
        String json = core.toJson(data);

        assertNotNull(json);
        assertTrue(json.contains("test"));
        assertTrue(json.contains("123"));
    }

    @Test
    @DisplayName("Should handle null in toJson")
    void testToJsonNull() {
        String json = core.toJson(null);
        assertEquals("null", json);
    }

    @Test
    @DisplayName("Should serialize to pretty JSON")
    void testToPrettyJson() {
        TestData data = new TestData("test", 123);
        String json = core.toPrettyJson(data);

        assertNotNull(json);
        assertTrue(json.contains("\n"), "Pretty JSON should have line breaks");
    }

    @Test
    @DisplayName("Should deserialize JSON to object")
    void testFromJson() {
        String json = "{\"name\":\"test\",\"value\":123}";
        TestData data = core.fromJson(json, TestData.class);

        assertNotNull(data);
        assertEquals("test", data.getName());
        assertEquals(123, data.getValue());
    }

    @Test
    @DisplayName("Should handle null JSON string")
    void testFromJsonNull() {
        TestData data = core.fromJson(null, TestData.class);
        assertNull(data);
    }

    @Test
    @DisplayName("Should handle empty JSON string")
    void testFromJsonEmpty() {
        TestData data = core.fromJson("", TestData.class);
        assertNull(data);
    }

    // ==================== Retry Tests ====================

    @Test
    @DisplayName("Should retry operation on failure")
    void testRetryWithBackoff() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = core.retryWithBackoff(() -> {
            int count = attempts.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("Attempt " + count + " failed");
            }
            return "Success";
        }, 3, 10);

        assertEquals("Success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("Should fail after max retries")
    void testRetryMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () -> {
            core.retryWithBackoff(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Always fails");
            }, 2, 10);
        });

        assertEquals(3, attempts.get(), "Should attempt 3 times (initial + 2 retries)");
    }

    @Test
    @DisplayName("Should retry with Duration backoff")
    void testRetryWithDuration() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = core.retryWithBackoff(() -> {
            int count = attempts.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("Failed");
            }
            return "Success";
        }, 2, Duration.ofMillis(10), Duration.ofMillis(100));

        assertEquals("Success", result);
        assertEquals(2, attempts.get());
    }

    // ==================== Async Tests ====================

    @Test
    @DisplayName("Should execute task asynchronously")
    void testExecuteAsync() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = core.executeAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Async Result";
        });

        String result = future.get();
        assertEquals("Async Result", result);
    }

    @Test
    @DisplayName("Should execute runnable asynchronously")
    void testExecuteAsyncRunnable() throws ExecutionException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        CompletableFuture<Void> future = core.executeAsync(() -> {
            counter.incrementAndGet();
        });

        future.get();
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("Should handle async exceptions")
    void testExecuteAsyncException() {
        CompletableFuture<String> future = core.executeAsync(() -> {
            throw new RuntimeException("Async error");
        });

        assertThrows(ExecutionException.class, future::get);
    }

    // ==================== String Utility Tests ====================

    @Test
    @DisplayName("Should check if string is blank")
    void testIsBlank() {
        assertTrue(core.isBlank(null));
        assertTrue(core.isBlank(""));
        assertTrue(core.isBlank("   "));
        assertFalse(core.isBlank("text"));
    }

    @Test
    @DisplayName("Should check if string is not blank")
    void testIsNotBlank() {
        assertFalse(core.isNotBlank(null));
        assertFalse(core.isNotBlank(""));
        assertFalse(core.isNotBlank("   "));
        assertTrue(core.isNotBlank("text"));
    }

    @Test
    @DisplayName("Should return null-safe value")
    void testNullSafe() {
        assertEquals("value", core.nullSafe("value", "default"));
        assertEquals("default", core.nullSafe(null, "default"));
    }

    @Test
    @DisplayName("Should truncate string")
    void testTruncate() {
        String text = "This is a long text";
        String truncated = core.truncate(text, 10);

        assertEquals(10, truncated.length());
        assertTrue(truncated.endsWith("..."));
    }

    @Test
    @DisplayName("Should not truncate short string")
    void testTruncateShort() {
        String text = "Short";
        String truncated = core.truncate(text, 10);

        assertEquals("Short", truncated);
    }

    // ==================== Hash Tests ====================

    @Test
    @DisplayName("Should generate MD5 hash")
    void testGenerateMD5() {
        String hash = core.generateMD5("test");

        assertNotNull(hash);
        assertEquals(32, hash.length(), "MD5 hash should be 32 characters");
        assertEquals("098f6bcd4621d373cade4e832627b4f6", hash);
    }

    @Test
    @DisplayName("Should generate SHA-256 hash")
    void testGenerateSHA256() {
        String hash = core.generateSHA256("test");

        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hash should be 64 characters");
    }

    @Test
    @DisplayName("Should generate hash with algorithm")
    void testGenerateHash() {
        String hash = core.generateHash("test", "SHA-1");

        assertNotNull(hash);
        assertEquals(40, hash.length(), "SHA-1 hash should be 40 characters");
    }

    @Test
    @DisplayName("Should handle null in hash generation")
    void testGenerateHashNull() {
        String hash = core.generateMD5(null);
        assertNull(hash);
    }

    @Test
    @DisplayName("Should throw exception for invalid algorithm")
    void testGenerateHashInvalidAlgorithm() {
        assertThrows(RuntimeException.class, () -> {
            core.generateHash("test", "INVALID");
        });
    }

    // ==================== ObjectMapper Tests ====================

    @Test
    @DisplayName("Should provide ObjectMapper")
    void testGetObjectMapper() {
        assertNotNull(core.getObjectMapper());
    }

    // ==================== Helper Class ====================

    public static class TestData {
        private String name;
        private int value;

        public TestData() {}

        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}

