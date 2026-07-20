package com.adhar.adharkit.logging.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Slf4jAppLogEventSink}.
 */
class Slf4jAppLogEventSinkTest {

    private AdharLoggingProperties properties;
    private Slf4jAppLogEventSink sink;
    private ListAppender<ILoggingEvent> appender;
    private Logger eventLogger;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new Slf4jAppLogEventSink(properties, new ObjectMapper());

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        eventLogger = context.getLogger("ADHAR_EVENT.BUSINESS");
        eventLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        eventLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        eventLogger.detachAppender(appender);
    }

    @Test
    void writesJsonToTypeSpecificLogger() {
        sink.onEvent(AppLogEvent.builder()
                .type(AppLogEventType.BUSINESS)
                .name("ORDER_PLACED")
                .metadata("orderId", "o-1")
                .build());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logEvent = appender.list.get(0);
        assertThat(logEvent.getLoggerName()).isEqualTo("ADHAR_EVENT.BUSINESS");
        assertThat(logEvent.getLevel()).isEqualTo(Level.INFO);
        assertThat(logEvent.getFormattedMessage())
                .contains("\"name\":\"ORDER_PLACED\"")
                .contains("\"orderId\":\"o-1\"");
    }

    @Test
    void mapsSeverityToLogLevel() {
        for (org.slf4j.event.Level severity : org.slf4j.event.Level.values()) {
            sink.onEvent(AppLogEvent.builder()
                    .type(AppLogEventType.BUSINESS)
                    .name("op")
                    .severity(severity)
                    .build());
        }

        assertThat(appender.list)
                .extracting(e -> e.getLevel().toString())
                .containsExactlyInAnyOrder("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    }

    @Test
    void fallsBackToToStringWhenSerializationFails() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new RuntimeException("no json"));
        Slf4jAppLogEventSink failingSink = new Slf4jAppLogEventSink(properties, failing);

        failingSink.onEvent(AppLogEvent.builder()
                .type(AppLogEventType.BUSINESS)
                .name("op")
                .build());

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("AppLogEvent");
    }

    @Test
    void usesConfiguredLoggerPrefix() {
        properties.getEvents().setLoggerPrefix("MY_EVENTS");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger custom = context.getLogger("MY_EVENTS.AUDIT");
        ListAppender<ILoggingEvent> customAppender = new ListAppender<>();
        customAppender.start();
        custom.addAppender(customAppender);
        try {
            sink.onEvent(AppLogEvent.builder().type(AppLogEventType.AUDIT).name("op").build());
            assertThat(customAppender.list).hasSize(1);
        } finally {
            custom.detachAppender(customAppender);
        }
    }
}
