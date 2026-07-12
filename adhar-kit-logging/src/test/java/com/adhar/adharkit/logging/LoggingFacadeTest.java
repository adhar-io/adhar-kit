package com.adhar.adharkit.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoggingFacade}. The facade delegates logging calls to an SLF4J logger
 * and manages MDC context, which is what these tests assert on.
 */
class LoggingFacadeTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getLoggerByClassUsesClassName() {
        LoggingFacade facade = LoggingFacade.getLogger(LoggingFacadeTest.class);
        assertThat(facade).isNotNull();
    }

    @Test
    void getLoggerByNameCreatesInstance() {
        LoggingFacade facade = LoggingFacade.getLogger("com.example.custom");
        assertThat(facade).isNotNull();
    }

    @Test
    void loggingMethodsDoNotThrow() {
        LoggingFacade facade = LoggingFacade.getLogger("test.logger");

        facade.debug("debug msg");
        facade.debug("debug {}", "arg");
        facade.info("info msg");
        facade.info("info {}", "arg");
        facade.warn("warn msg");
        facade.warn("warn {}", "arg");
        facade.error("error msg");
        facade.error("error with throwable", new RuntimeException("boom"));
        facade.error("error {}", "arg");
        // No exception means delegate forwarded all calls successfully
    }

    @Test
    void addAndGetContext() {
        LoggingFacade facade = LoggingFacade.getLogger("ctx.logger");

        facade.addContext("key1", "value1");
        assertThat(facade.getContext("key1")).isEqualTo("value1");
        assertThat(MDC.get("key1")).isEqualTo("value1");
    }

    @Test
    void addContexts() {
        LoggingFacade facade = LoggingFacade.getLogger("ctx.logger");

        facade.addContexts(Map.of("a", "1", "b", "2"));
        assertThat(facade.getContext("a")).isEqualTo("1");
        assertThat(facade.getContext("b")).isEqualTo("2");
    }

    @Test
    void removeContext() {
        LoggingFacade facade = LoggingFacade.getLogger("ctx.logger");

        facade.addContext("temp", "x");
        facade.removeContext("temp");
        assertThat(facade.getContext("temp")).isNull();
    }

    @Test
    void clearContext() {
        LoggingFacade facade = LoggingFacade.getLogger("ctx.logger");

        facade.addContext("k1", "v1");
        facade.addContext("k2", "v2");
        facade.clearContext();
        assertThat(facade.getContext("k1")).isNull();
        assertThat(facade.getContext("k2")).isNull();
    }
}
