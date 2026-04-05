package com.adhar.adharkit.logging.helidon;

import com.adhar.adharkit.logging.api.LoggingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Helidon-specific adapter for logging using SLF4J.
 *
 * <p>This adapter provides a standalone logging implementation for Helidon SE and MP
 * applications. It uses SLF4J as the logging facade, which bridges to any logging
 * backend (java.util.logging, Logback, Log4j2).</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>SLF4J:</b> Universal logging facade compatible with all backends</li>
 *   <li><b>MDC Support:</b> Full MDC (Mapped Diagnostic Context) for contextual logging</li>
 *   <li><b>Configurable Logger:</b> Accepts a custom logger name or uses a default</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * HelidonLoggingAdapter logging = new HelidonLoggingAdapter();
 * logging.addContext("requestId", UUID.randomUUID().toString());
 * logging.info("Processing request for user {}", userId);
 * logging.clearContext();
 * }</pre>
 *
 * <p><b>Helidon Logging Configuration:</b></p>
 * <p>Helidon uses java.util.logging by default. To use SLF4J with Logback,
 * add the jul-to-slf4j bridge and logback-classic to the classpath.</p>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. SLF4J and MDC are designed for concurrent use.
 * MDC values are thread-local, making them safe for Helidon's request-per-thread model.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see LoggingService
 * @see FrameworkAdapter
 */
public class HelidonLoggingAdapter implements FrameworkAdapter<LoggingService>, LoggingService {

    /**
     * SLF4J logger instance used for all logging operations.
     */
    private final Logger log;

    /**
     * Constructs a HelidonLoggingAdapter with a custom logger name.
     *
     * @param loggerName the name for the SLF4J logger
     */
    public HelidonLoggingAdapter(String loggerName) {
        this.log = LoggerFactory.getLogger(loggerName);
    }

    /**
     * Constructs a HelidonLoggingAdapter with the default logger name.
     */
    public HelidonLoggingAdapter() {
        this.log = LoggerFactory.getLogger(HelidonLoggingAdapter.class);
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    @Override
    public LoggingService getService() {
        return this;
    }

    @Override
    public void debug(String message) {
        log.debug(message);
    }

    @Override
    public void debug(String message, Object... args) {
        log.debug(message, args);
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void info(String message, Object... args) {
        log.info(message, args);
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void warn(String message, Object... args) {
        log.warn(message, args);
    }

    @Override
    public void error(String message) {
        log.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        log.error(message, throwable);
    }

    @Override
    public void error(String message, Object... args) {
        log.error(message, args);
    }

    @Override
    public void addContext(String key, String value) {
        MDC.put(key, value);
    }

    @Override
    public void addContexts(Map<String, String> contexts) {
        contexts.forEach(MDC::put);
    }

    @Override
    public void removeContext(String key) {
        MDC.remove(key);
    }

    @Override
    public void clearContext() {
        MDC.clear();
    }

    @Override
    public String getContext(String key) {
        return MDC.get(key);
    }
}
