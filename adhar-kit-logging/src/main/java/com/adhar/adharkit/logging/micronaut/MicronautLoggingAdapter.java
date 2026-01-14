package com.adhar.adharkit.logging.micronaut;

import com.adhar.adharkit.logging.api.LoggingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Micronaut implementation of Logging Service using SLF4J.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautLoggingAdapter implements FrameworkAdapter<LoggingService>, LoggingService {

    private static final Logger log = LoggerFactory.getLogger(MicronautLoggingAdapter.class);

    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
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

