package com.adhar.kit.core;

import com.adhar.kit.core.config.AdharCoreProperties;
import com.adhar.kit.core.util.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Core utilities facade for common operations across all frameworks.
 *
 * <p>Provides essential utility functions for ID generation, JSON serialization,
 * retry logic, async execution, and more. Works seamlessly with Spring Boot,
 * Quarkus, and Micronaut.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Unique ID generation (UUID, short ID, snowflake-style)</li>
 *   <li>JSON serialization/deserialization</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Async execution with thread pool</li>
 *   <li>String utilities (null-safe, validation)</li>
 *   <li>Hash generation (MD5, SHA-256)</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * CoreFacade core = CoreFacade.getInstance();
 *
 * // Generate unique IDs
 * String requestId = core.generateId();           // Compact ID
 * String uuid = core.generateUUID();              // Standard UUID
 * String correlationId = core.generateShortId();  // 8-char short ID
 *
 * // JSON operations
 * String json = core.toJson(myObject);
 * MyObject obj = core.fromJson(json, MyObject.class);
 *
 * // Retry with exponential backoff
 * Result result = core.retryWithBackoff(
 *     () -> callExternalApi(),
 *     3,      // max retries
 *     1000    // initial delay ms
 * );
 *
 * // Execute async
 * CompletableFuture<Result> future = core.executeAsync(() -> {
 *     return performLongRunningTask();
 * });
 *
 * // String utilities
 * boolean isEmpty = core.isBlank(str);
 * String safe = core.nullSafe(str, "default");
 *
 * // Hash generation
 * String hash = core.generateHash("data", "SHA-256");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class CoreFacade {

    private static volatile CoreFacade instance;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private CoreFacade() {
        log.info("Initializing CoreFacade v1.0.0");

        // Initialize ObjectMapper with Java 8 date/time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Create thread pool for async operations
        this.asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            r -> {
                Thread t = new Thread(r);
                t.setName("adhar-core-async-" + t.getId());
                t.setDaemon(true);
                return t;
            }
        );

        // Auto-resolved node id (env/system property/hostname fallback)
        this.snowflakeIdGenerator = new SnowflakeIdGenerator();

        log.info("CoreFacade initialized successfully");
    }

    /**
     * DI-friendly constructor for container-managed use (e.g. Spring's
     * {@code CoreAutoConfiguration}). Non-Spring code should keep using
     * {@link #getInstance()}.
     *
     * @param properties core configuration properties
     * @param asyncExecutor executor for async operations
     * @param objectMapper ObjectMapper for JSON operations
     */
    public CoreFacade(AdharCoreProperties properties, Executor asyncExecutor, ObjectMapper objectMapper) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.snowflakeIdGenerator = new SnowflakeIdGenerator(properties.getSnowflake().getNodeId());
        log.info("CoreFacade initialized from configuration properties");
    }

    /**
     * Gets the singleton instance of CoreFacade.
     *
     * @return CoreFacade instance
     */
    public static CoreFacade getInstance() {
        if (instance == null) {
            synchronized (CoreFacade.class) {
                if (instance == null) {
                    instance = new CoreFacade();
                }
            }
        }
        return instance;
    }

    // ==================== ID Generation ====================

    /**
     * Generates a compact unique ID (UUID without hyphens).
     *
     * @return unique ID string (32 characters)
     */
    public String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates a standard UUID.
     *
     * @return UUID string (36 characters with hyphens)
     */
    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a short ID (8 characters) for correlation IDs.
     *
     * @return short ID string
     */
    public String generateShortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a snowflake ID (64-bit, time-ordered, unique across nodes).
     *
     * @return numeric ID
     * @see SnowflakeIdGenerator
     */
    public long generateSnowflakeId() {
        return snowflakeIdGenerator.nextId();
    }

    // ==================== JSON Operations ====================

    /**
     * Converts an object to JSON string.
     *
     * @param object the object to serialize
     * @return JSON string
     * @throws RuntimeException if serialization fails
     */
    public String toJson(Object object) {
        if (object == null) {
            return "null";
        }

        try {
            String json = objectMapper.writeValueAsString(object);
            log.debug("Serialized {} to JSON ({} bytes)",
                object.getClass().getSimpleName(), json.length());
            return json;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", object.getClass(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Converts an object to pretty-printed JSON string.
     *
     * @param object the object to serialize
     * @return pretty-printed JSON string
     * @throws RuntimeException if serialization fails
     */
    public String toPrettyJson(Object object) {
        if (object == null) {
            return "null";
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to pretty JSON", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Converts JSON string to an object.
     *
     * @param json the JSON string
     * @param type the target class
     * @param <T> the type
     * @return the deserialized object
     * @throws RuntimeException if deserialization fails
     */
    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            T result = objectMapper.readValue(json, type);
            log.debug("Deserialized JSON to {}", type.getSimpleName());
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", type.getSimpleName(), json, e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    // ==================== Retry Logic ====================

    /**
     * Executes an operation with retry and exponential backoff.
     *
     * @param operation the operation to execute
     * @param maxRetries maximum number of retry attempts
     * @param initialDelayMs initial delay in milliseconds
     * @param <T> the return type
     * @return the operation result
     * @throws RuntimeException if all retries fail
     */
    public <T> T retryWithBackoff(Supplier<T> operation, int maxRetries, long initialDelayMs) {
        int attempt = 0;
        long delay = initialDelayMs;

        while (true) {
            try {
                attempt++;
                log.debug("Executing operation (attempt {}/{})", attempt, maxRetries + 1);
                return operation.get();

            } catch (Exception e) {
                if (attempt > maxRetries) {
                    log.error("Operation failed after {} attempts", attempt, e);
                    throw new RuntimeException("Operation failed after " + attempt + " attempts", e);
                }

                log.warn("Operation failed (attempt {}/{}), retrying in {}ms: {}",
                    attempt, maxRetries + 1, delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                // Exponential backoff with jitter
                delay = (long) (delay * 2 * (0.8 + Math.random() * 0.4));
            }
        }
    }

    /**
     * Executes an operation with retry and configurable backoff.
     *
     * @param operation the operation to execute
     * @param maxRetries maximum number of retry attempts
     * @param initialDelay initial delay duration
     * @param maxDelay maximum delay duration
     * @param <T> the return type
     * @return the operation result
     */
    public <T> T retryWithBackoff(Supplier<T> operation, int maxRetries,
                                   Duration initialDelay, Duration maxDelay) {
        int attempt = 0;
        long delayMs = initialDelay.toMillis();
        long maxDelayMs = maxDelay.toMillis();

        while (true) {
            try {
                attempt++;
                return operation.get();

            } catch (Exception e) {
                if (attempt > maxRetries) {
                    throw new RuntimeException("Operation failed after " + attempt + " attempts", e);
                }

                log.warn("Retry attempt {}/{} after {}ms", attempt, maxRetries + 1, delayMs);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                // Exponential backoff capped at maxDelay
                delayMs = Math.min(delayMs * 2, maxDelayMs);
            }
        }
    }

    // ==================== Async Execution ====================

    /**
     * Executes a task asynchronously.
     *
     * @param task the task to execute
     * @param <T> the return type
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, asyncExecutor)
            .whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Async task failed", error);
                } else {
                    log.debug("Async task completed successfully");
                }
            });
    }

    /**
     * Executes a runnable task asynchronously.
     *
     * @param task the task to execute
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> executeAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor)
            .whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Async task failed", error);
                }
            });
    }

    // ==================== String Utilities ====================

    /**
     * Checks if a string is null or blank.
     *
     * @param str the string to check
     * @return true if null or blank
     */
    public boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if a string is not blank.
     *
     * @param str the string to check
     * @return true if not null and not blank
     */
    public boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Returns a default value if the string is null.
     *
     * @param str the string to check
     * @param defaultValue the default value
     * @return the string or default value
     */
    public String nullSafe(String str, String defaultValue) {
        return str != null ? str : defaultValue;
    }

    /**
     * Truncates a string to the specified length.
     *
     * @param str the string to truncate
     * @param maxLength maximum length
     * @return truncated string
     */
    public String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    // ==================== Hash Generation ====================

    /**
     * Generates a hash for the given data using the specified algorithm.
     *
     * @param data the data to hash
     * @param algorithm the hash algorithm (MD5, SHA-1, SHA-256, SHA-512)
     * @return hex string of the hash
     */
    public String generateHash(String data, String algorithm) {
        if (data == null) {
            return null;
        }

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Failed to generate hash with algorithm: {}", algorithm, e);
            throw new RuntimeException("Hash generation failed", e);
        }
    }

    /**
     * Generates an MD5 hash.
     *
     * @param data the data to hash
     * @return MD5 hash string
     */
    public String generateMD5(String data) {
        return generateHash(data, "MD5");
    }

    /**
     * Generates a SHA-256 hash.
     *
     * @param data the data to hash
     * @return SHA-256 hash string
     */
    public String generateSHA256(String data) {
        return generateHash(data, "SHA-256");
    }

    // ==================== Helper Methods ====================

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Gets the configured ObjectMapper for custom JSON operations.
     *
     * @return ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Gets the async executor for custom async operations.
     *
     * @return Executor instance
     */
    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }
}

