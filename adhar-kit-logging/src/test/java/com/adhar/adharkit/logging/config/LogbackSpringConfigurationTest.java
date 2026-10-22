package com.adhar.adharkit.logging.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the kit's {@code logback-spring.xml} through Spring Boot's logging system, as an
 * application does, and checks what a Kubernetes pod prints: ONE console appender writing
 * JSON with level/message/logger and the trace ids from the MDC — and none of this file's
 * own settings. (It printed every line twice, text and JSON, with ~40 config keys inside.)
 */
class LogbackSpringConfigurationTest {

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void reset() {
        loggingSystem.cleanUp();
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
    }

    private Logger initialize(MockEnvironment environment) {
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    }

    private static List<Appender<ILoggingEvent>> appenders(Logger root) {
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            appenders.add(it.next());
        }
        return appenders;
    }

    @Test
    void writesOneJsonLinePerEventWithTraceIdsAndNoConfigKeys() throws Exception {
        Logger root = initialize(new MockEnvironment()
                .withProperty("spring.application.name", "orders")
                .withProperty("adhar.environment", "dev")
                .withProperty("adhar.logging.file.enabled", "false"));

        List<Appender<ILoggingEvent>> consoles = appenders(root).stream()
                .filter(appender -> appender instanceof ConsoleAppender).toList();
        assertThat(consoles).hasSize(1);
        assertThat(appenders(root)).extracting(Appender::getName).containsExactly("CONSOLE_JSON");

        Logger orders = root.getLoggerContext().getLogger("com.example.orders.PaymentService");
        LoggingEvent event = new LoggingEvent(getClass().getName(), orders, Level.ERROR, "payment failed", null, null);
        event.setMDCPropertyMap(Map.of("traceId", "4bf92f3577b34da6a3ce929d0e0e4736", "spanId", "00f067aa0ba902b7"));
        @SuppressWarnings("unchecked")
        ConsoleAppender<ILoggingEvent> console = (ConsoleAppender<ILoggingEvent>) consoles.get(0);
        String line = new String(console.getEncoder().encode(event), StandardCharsets.UTF_8);

        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.path("level").asText()).isEqualTo("ERROR");
        assertThat(json.path("message").asText()).isEqualTo("payment failed");
        assertThat(json.path("logger").asText()).isEqualTo("com.example.orders.PaymentService");
        assertThat(json.path("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(json.path("spanId").asText()).isEqualTo("00f067aa0ba902b7");
        assertThat(json.path("app_name").asText()).isEqualTo("orders");
        assertThat(json.path("env").asText()).isEqualTo("dev");
        assertThat(json.has("JSON_LEVEL_FIELD")).isFalse();
        assertThat(json.has("LOGSTASH_HOST")).isFalse();
        assertThat(json.has("APP_NAME")).isFalse();
    }

    @Test
    void textModeIsAlsoASingleConsole() {
        Logger root = initialize(new MockEnvironment()
                .withProperty("adhar.logging.console.json", "false")
                .withProperty("adhar.logging.file.enabled", "false"));
        assertThat(appenders(root)).extracting(Appender::getName).containsExactly("CONSOLE_TEXT");
    }
}
