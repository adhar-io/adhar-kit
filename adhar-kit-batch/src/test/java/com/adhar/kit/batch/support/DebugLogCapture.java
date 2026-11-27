package com.adhar.kit.batch.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Turns one class's logger to DEBUG and records what it logs, until closed.
 */
public final class DebugLogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previous;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public DebugLogCapture(Class<?> type) {
        logger = (Logger) LoggerFactory.getLogger(type);
        previous = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    /**
     * @return every message logged so far, rendered
     */
    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * @return every event logged so far, in order
     */
    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }
}
