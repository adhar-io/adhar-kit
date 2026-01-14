package com.adhar.adharkit.logging.quarkus;

import com.adhar.adharkit.logging.api.LoggingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.Map;

/**
 * Quarkus implementation of Logging Service using JBoss Logging.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@ApplicationScoped
public class QuarkusLoggingAdapter implements FrameworkAdapter<LoggingService>, LoggingService {

    private static final Logger log = Logger.getLogger(QuarkusLoggingAdapter.class);

    @Override
    public Framework getSupportedFramework() {
        return Framework.QUARKUS;
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
        log.debugf(message, args);
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void info(String message, Object... args) {
        log.infof(message, args);
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void warn(String message, Object... args) {
        log.warnf(message, args);
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
        log.errorf(message, args);
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
        return (String) MDC.get(key);
    }
}

