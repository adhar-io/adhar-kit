package com.adhar.adharkit.logging.api;

import java.util.Map;

/**
 * Framework-agnostic Logging Service.
 * Works with Spring Boot, Quarkus, and Micronaut.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface LoggingService {

    /**
     * Log at DEBUG level.
     *
     * @param message log message
     */
    void debug(String message);

    /**
     * Log at DEBUG level with arguments.
     *
     * @param message log message with placeholders
     * @param args arguments
     */
    void debug(String message, Object... args);

    /**
     * Log at INFO level.
     *
     * @param message log message
     */
    void info(String message);

    /**
     * Log at INFO level with arguments.
     *
     * @param message log message with placeholders
     * @param args arguments
     */
    void info(String message, Object... args);

    /**
     * Log at WARN level.
     *
     * @param message log message
     */
    void warn(String message);

    /**
     * Log at WARN level with arguments.
     *
     * @param message log message with placeholders
     * @param args arguments
     */
    void warn(String message, Object... args);

    /**
     * Log at ERROR level.
     *
     * @param message log message
     */
    void error(String message);

    /**
     * Log at ERROR level with exception.
     *
     * @param message log message
     * @param throwable exception
     */
    void error(String message, Throwable throwable);

    /**
     * Log at ERROR level with arguments.
     *
     * @param message log message with placeholders
     * @param args arguments
     */
    void error(String message, Object... args);

    /**
     * Add MDC context.
     *
     * @param key context key
     * @param value context value
     */
    void addContext(String key, String value);

    /**
     * Add multiple MDC contexts.
     *
     * @param contexts map of contexts
     */
    void addContexts(Map<String, String> contexts);

    /**
     * Remove MDC context.
     *
     * @param key context key
     */
    void removeContext(String key);

    /**
     * Clear all MDC contexts.
     */
    void clearContext();

    /**
     * Get MDC context value.
     *
     * @param key context key
     * @return context value or null
     */
    String getContext(String key);
}

