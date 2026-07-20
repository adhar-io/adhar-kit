package com.adhar.adharkit.logging.event;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Default {@link AppLogEventSink} that writes each event as a single JSON line to a dedicated
 * SLF4J logger named {@code <prefix>.<type>} (e.g. {@code ADHAR_EVENT.BUSINESS},
 * {@code ADHAR_EVENT.AUDIT}), at the event's severity level.
 *
 * <p>Using per-type logger names lets operators route business, audit, API and batch events to
 * separate appenders/indices with plain logback configuration.</p>
 */
public class Slf4jAppLogEventSink implements AppLogEventSink {

    private final AdharLoggingProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Creates the sink.
     *
     * @param properties   logging properties (event logger prefix)
     * @param objectMapper mapper used to render events as JSON
     */
    public Slf4jAppLogEventSink(AdharLoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onEvent(AppLogEvent event) {
        Logger logger = LoggerFactory.getLogger(
                properties.getEvents().getLoggerPrefix() + "." + event.getType().name());
        String json = toJson(event);
        Level level = event.getSeverity() != null ? event.getSeverity() : Level.INFO;
        switch (level) {
            case ERROR -> logger.error("{}", json);
            case WARN -> logger.warn("{}", json);
            case INFO -> logger.info("{}", json);
            case DEBUG -> logger.debug("{}", json);
            case TRACE -> logger.trace("{}", json);
        }
    }

    private String toJson(AppLogEvent event) {
        try {
            return objectMapper.writeValueAsString(event.toMap());
        } catch (Exception e) {
            return event.toString();
        }
    }
}
