package com.adhar.kit.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for CoreFacade.
 */
@DisplayName("CoreFacade Tests")
class CoreFacadeTest {

    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final CoreFacade core = CoreFacade.getInstance();

    // ==================== Singleton ====================

    @Test
    @DisplayName("getInstance returns the same instance on repeated calls")
    void getInstance_returnsSameInstance() {
        CoreFacade instance1 = CoreFacade.getInstance();
        CoreFacade instance2 = CoreFacade.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    // ==================== ID Generation ====================

    @Nested
    @DisplayName("ID Generation")
    class IdGeneration {

        @Test
        @DisplayName("generateId returns non-null, non-empty 32-char string without hyphens")
        void generateId_returnsCompactUuid() {
            String id = core.generateId();

            assertThat(id)
                .isNotNull()
                .isNotEmpty()
                .hasSize(32)
                .doesNotContain("-");
        }

        @Test
        @DisplayName("generateId produces unique values across calls")
        void generateId_producesUniqueValues() {
            String id1 = core.generateId();
            String id2 = core.generateId();

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("generateUUID returns valid UUID format")
        void generateUUID_returnsValidFormat() {
            String uuid = core.generateUUID();

            assertThat(uuid)
                .isNotNull()
                .hasSize(36)
                .matches(UUID_PATTERN);
        }

        @Test
        @DisplayName("generateShortId returns non-null 8-char string shorter than UUID")
        void generateShortId_returnsShorterThanUuid() {
            String shortId = core.generateShortId();
            String uuid = core.generateUUID();

            assertThat(shortId)
                .isNotNull()
                .hasSize(8);
            assertThat(shortId.length()).isLessThan(uuid.length());
        }

        @Test
        @DisplayName("generateSnowflakeId returns positive number, unique across calls")
        void generateSnowflakeId_returnsPositiveAndUnique() {
            long id1 = core.generateSnowflakeId();
            long id2 = core.generateSnowflakeId();

            assertThat(id1).isPositive();
            assertThat(id2).isPositive();
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    // ==================== JSON Operations ====================

    @Nested
    @DisplayName("JSON Operations")
    class JsonOperations {

        @Test
        @DisplayName("toJson serializes object correctly")
        void toJson_serializesObject() {
            TestData data = new TestData("hello", 42);
            String json = core.toJson(data);

            assertThat(json)
                .isNotNull()
                .contains("\"name\"")
                .contains("\"hello\"")
                .contains("42");
        }

        @Test
        @DisplayName("toJson returns 'null' string for null input")
        void toJson_handlesNull() {
            assertThat(core.toJson(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("toPrettyJson includes formatting (newlines/indentation)")
        void toPrettyJson_includesFormatting() {
            TestData data = new TestData("test", 1);
            String pretty = core.toPrettyJson(data);

            assertThat(pretty)
                .isNotNull()
                .contains("\n")
                .contains("\"name\"");
        }

        @Test
        @DisplayName("toPrettyJson returns 'null' string for null input")
        void toPrettyJson_handlesNull() {
            assertThat(core.toPrettyJson(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("fromJson deserializes correctly")
        void fromJson_deserializesCorrectly() {
            String json = "{\"name\":\"world\",\"value\":99}";
            TestData data = core.fromJson(json, TestData.class);

            assertThat(data).isNotNull();
            assertThat(data.getName()).isEqualTo("world");
            assertThat(data.getValue()).isEqualTo(99);
        }

        @Test
        @DisplayName("fromJson returns null for null input")
        void fromJson_returnsNullForNull() {
            assertThat(core.fromJson(null, TestData.class)).isNull();
        }

        @Test
        @DisplayName("fromJson returns null for empty/blank input")
        void fromJson_returnsNullForEmpty() {
            assertThat(core.fromJson("", TestData.class)).isNull();
            assertThat(core.fromJson("   ", TestData.class)).isNull();
        }

        @Test
        @DisplayName("fromJson with invalid JSON throws RuntimeException")
        void fromJson_invalidJsonThrows() {
            assertThatThrownBy(() -> core.fromJson("{invalid", TestData.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deserialization failed");
        }
    }

    // ==================== Retry Logic ====================

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogic {

        @Test
        @DisplayName("retryWithBackoff succeeds on first try without retrying")
        void retryWithBackoff_succeedsFirstTry() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = core.retryWithBackoff(() -> {
                attempts.incrementAndGet();
                return "immediate";
            }, 3, 10);

            assertThat(result).isEqualTo("immediate");
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("retryWithBackoff retries on failure then succeeds")
        void retryWithBackoff_retriesThenSucceeds() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = core.retryWithBackoff(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new RuntimeException("not yet");
                }
                return "done";
            }, 3, 10);

            assertThat(result).isEqualTo("done");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("retryWithBackoff exhausts retries and throws")
        void retryWithBackoff_exhaustsRetries() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThatThrownBy(() -> core.retryWithBackoff(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("always fails");
            }, 2, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("failed after");

            assertThat(attempts.get()).isEqualTo(3); // 1 initial + 2 retries
        }

        @Test
        @DisplayName("retryWithBackoff with Duration overload works correctly")
        void retryWithBackoff_durationOverload() {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = core.retryWithBackoff(() -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new RuntimeException("fail");
                }
                return "ok";
            }, 2, Duration.ofMillis(10), Duration.ofMillis(100));

            assertThat(result).isEqualTo("ok");
            assertThat(attempts.get()).isEqualTo(2);
        }
    }

    // ==================== Async Execution ====================

    @Nested
    @DisplayName("Async Execution")
    class AsyncExecution {

        @Test
        @DisplayName("executeAsync(Supplier) returns CompletableFuture that completes")
        void executeAsync_supplierCompletes() throws Exception {
            CompletableFuture<String> future = core.executeAsync(() -> "async result");

            assertThat(future.get()).isEqualTo("async result");
        }

        @Test
        @DisplayName("executeAsync(Runnable) completes without error")
        void executeAsync_runnableCompletes() throws Exception {
            AtomicInteger counter = new AtomicInteger(0);

            CompletableFuture<Void> future = core.executeAsync((Runnable) () -> counter.incrementAndGet());
            future.get();

            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("executeAsync propagates exceptions via ExecutionException")
        void executeAsync_propagatesException() {
            CompletableFuture<String> future = core.executeAsync(() -> {
                throw new RuntimeException("boom");
            });

            assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class);
        }
    }

    // ==================== String Utilities ====================

    @Nested
    @DisplayName("String Utilities")
    class StringUtilities {

        @Test
        @DisplayName("isBlank returns true for null, empty, whitespace")
        void isBlank_trueForNullEmptyWhitespace() {
            assertThat(core.isBlank(null)).isTrue();
            assertThat(core.isBlank("")).isTrue();
            assertThat(core.isBlank("   ")).isTrue();
            assertThat(core.isBlank("\t\n")).isTrue();
        }

        @Test
        @DisplayName("isBlank returns false for text with content")
        void isBlank_falseForContent() {
            assertThat(core.isBlank("text")).isFalse();
            assertThat(core.isBlank(" a ")).isFalse();
        }

        @Test
        @DisplayName("isNotBlank is the inverse of isBlank")
        void isNotBlank_inverseOfIsBlank() {
            assertThat(core.isNotBlank(null)).isFalse();
            assertThat(core.isNotBlank("")).isFalse();
            assertThat(core.isNotBlank("   ")).isFalse();
            assertThat(core.isNotBlank("text")).isTrue();
        }

        @Test
        @DisplayName("nullSafe returns value when non-null, default when null")
        void nullSafe_returnsCorrectly() {
            assertThat(core.nullSafe("value", "default")).isEqualTo("value");
            assertThat(core.nullSafe(null, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("truncate returns truncated string at max length with ellipsis")
        void truncate_truncatesLongString() {
            String result = core.truncate("This is a long text that should be truncated", 15);

            assertThat(result).hasSize(15);
            assertThat(result).endsWith("...");
        }

        @Test
        @DisplayName("truncate returns original when shorter than max")
        void truncate_returnsOriginalWhenShorter() {
            assertThat(core.truncate("short", 100)).isEqualTo("short");
        }

        @Test
        @DisplayName("truncate handles null input")
        void truncate_handlesNull() {
            assertThat(core.truncate(null, 10)).isNull();
        }
    }

    // ==================== Hash Generation ====================

    @Nested
    @DisplayName("Hash Generation")
    class HashGeneration {

        @Test
        @DisplayName("generateHash returns a hex hash string")
        void generateHash_returnsHexString() {
            String hash = core.generateHash("test", "SHA-1");

            assertThat(hash)
                .isNotNull()
                .hasSize(40) // SHA-1 produces 40 hex chars
                .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("generateHash returns null for null data")
        void generateHash_nullDataReturnsNull() {
            assertThat(core.generateHash(null, "SHA-256")).isNull();
        }

        @Test
        @DisplayName("generateHash throws for invalid algorithm")
        void generateHash_invalidAlgorithmThrows() {
            assertThatThrownBy(() -> core.generateHash("data", "INVALID"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Hash generation failed");
        }

        @Test
        @DisplayName("generateMD5 returns 32-char hex string")
        void generateMD5_returns32CharHex() {
            String hash = core.generateMD5("test");

            assertThat(hash)
                .isNotNull()
                .hasSize(32)
                .matches("[0-9a-f]+")
                .isEqualTo("098f6bcd4621d373cade4e832627b4f6");
        }

        @Test
        @DisplayName("generateSHA256 returns 64-char hex string")
        void generateSHA256_returns64CharHex() {
            String hash = core.generateSHA256("test");

            assertThat(hash)
                .isNotNull()
                .hasSize(64)
                .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("generateMD5 returns null for null input")
        void generateMD5_nullReturnsNull() {
            assertThat(core.generateMD5(null)).isNull();
        }
    }

    // ==================== Accessors ====================

    @Test
    @DisplayName("getObjectMapper returns non-null instance")
    void getObjectMapper_returnsNonNull() {
        assertThat(core.getObjectMapper()).isNotNull();
    }

    @Test
    @DisplayName("getAsyncExecutor returns non-null instance")
    void getAsyncExecutor_returnsNonNull() {
        assertThat(core.getAsyncExecutor()).isNotNull();
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

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}
