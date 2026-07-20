package com.adhar.adharkit.security.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adhar.kit.security.audit.SecurityAuditLogger.SecurityEventType;
import com.adhar.kit.security.audit.Slf4jAuditEventSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Slf4jAuditEventSink}.
 */
class Slf4jAuditEventSinkTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    private ILoggingEvent lastEvent() {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(appender.list.size() - 1);
    }

    @Test
    void publishesEventAsJacksonJsonAtInfoLevel() {
        Slf4jAuditEventSink sink = new Slf4jAuditEventSink();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "AUTHENTICATION_SUCCESS");
        data.put("principal", "alice");
        data.put("count", 3);
        data.put("active", true);
        data.put("tags", List.of("a", "b"));
        data.put("nested", Map.of("k", "v"));
        data.put("missing", null);

        sink.publish(SecurityEventType.AUTHENTICATION_SUCCESS, data);

        ILoggingEvent event = lastEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).startsWith("SECURITY_AUDIT: ");
        assertThat(msg).contains("\"event\":\"AUTHENTICATION_SUCCESS\"");
        assertThat(msg).contains("\"principal\":\"alice\"");
        assertThat(msg).contains("\"count\":3");
        assertThat(msg).contains("\"active\":true");
        assertThat(msg).contains("\"tags\":[\"a\",\"b\"]");
        assertThat(msg).contains("\"nested\":{\"k\":\"v\"}");
        assertThat(msg).contains("\"missing\":null");
    }

    @Test
    void publishesFailureEventsAtWarnLevel() {
        Slf4jAuditEventSink sink = new Slf4jAuditEventSink();

        sink.publish(SecurityEventType.AUTHENTICATION_FAILURE_BAD_CREDENTIALS,
            Map.of("event", "AUTHENTICATION_FAILURE_BAD_CREDENTIALS"));

        assertThat(lastEvent().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void escapesSpecialCharactersViaJackson() {
        Slf4jAuditEventSink sink = new Slf4jAuditEventSink();

        sink.publish(SecurityEventType.LOGOUT_SUCCESS, Map.of("message", "Bad \"creds\"\nline"));

        String msg = lastEvent().getFormattedMessage();
        assertThat(msg).contains("Bad \\\"creds\\\"\\nline");
    }

    @Test
    void serializationFailureIsSwallowed() throws JsonProcessingException {
        ObjectMapper failing = Mockito.mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        Slf4jAuditEventSink sink = new Slf4jAuditEventSink(failing);

        sink.publish(SecurityEventType.AUTHENTICATION_SUCCESS, Map.of("k", "v"));

        // Nothing written to the audit logger, and no exception propagated.
        assertThat(appender.list).isEmpty();
    }
}
