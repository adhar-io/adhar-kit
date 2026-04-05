package com.adhar.adharkit.logging.vertx;

import com.adhar.adharkit.logging.api.LoggingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Vert.x adapter for logging using SLF4J.
 *
 * <p>This adapter provides logging integration for Vert.x applications using the
 * SLF4J API with MDC (Mapped Diagnostic Context) support. Since Vert.x does not
 * have a built-in dependency injection container, this adapter is instantiated
 * directly and requires no framework-specific annotations.</p>
 *
 * <p>Vert.x supports SLF4J natively when a compatible logging backend (such as
 * Logback or Log4j2) is on the classpath. By default, Vert.x uses JUL (java.util.logging),
 * but SLF4J can be enabled by setting the {@code vertx.logger-delegate-factory-class-name}
 * system property.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization, no annotations needed</li>
 *   <li><b>SLF4J Native:</b> Uses standard SLF4J API with {} placeholder syntax</li>
 *   <li><b>MDC Support:</b> Full MDC context propagation for structured logging</li>
 *   <li><b>Logback/Log4j2:</b> Compatible with any SLF4J backend</li>
 *   <li><b>Event Loop Safe:</b> All logging operations are non-blocking</li>
 * </ul>
 *
 * <p><b>Vert.x SLF4J Configuration:</b></p>
 * <pre>{@code
 * // Set before creating the Vertx instance
 * System.setProperty("vertx.logger-delegate-factory-class-name",
 *     "io.vertx.core.logging.SLF4JLogDelegateFactory");
 * }</pre>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * VertxLoggingAdapter logging = new VertxLoggingAdapter();
 *
 * public class OrderVerticle extends AbstractVerticle {
 *     private final VertxLoggingAdapter logging = new VertxLoggingAdapter();
 *
 *     public void start() {
 *         logging.addContext("verticle", "OrderVerticle");
 *         logging.info("Order verticle started on event loop");
 *
 *         vertx.eventBus().consumer("orders.create", msg -> {
 *             logging.addContext("orderId", msg.body().toString());
 *             logging.info("Processing order: {}", msg.body());
 *             logging.removeContext("orderId");
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>MDC with Vert.x Context:</b></p>
 * <p>When using Vert.x's event loop, MDC values are local to the current thread.
 * For proper MDC propagation across async boundaries, consider using Vert.x's
 * context-local data or a dedicated MDC propagation strategy.</p>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. SLF4J loggers are thread-safe, and MDC operations
 * are thread-local by design. Each Vert.x event loop thread maintains its own
 * MDC context.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see LoggingService
 * @see FrameworkAdapter
 */
public class VertxLoggingAdapter implements FrameworkAdapter<LoggingService>, LoggingService {

    private static final Logger log = LoggerFactory.getLogger(VertxLoggingAdapter.class);

    /**
     * Constructs a new Vert.x logging adapter.
     *
     * <p>No external dependencies are required. The adapter uses the SLF4J
     * LoggerFactory to obtain loggers and the SLF4J MDC for context management.</p>
     */
    public VertxLoggingAdapter() {
        log.debug("Initialized Vert.x Logging adapter using SLF4J");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
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
