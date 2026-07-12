package com.adhar.adharkit.logging.spring;

import com.adhar.adharkit.logging.api.LoggingService;
import com.adhar.kit.commons.framework.Framework;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SpringLoggingAdapter}.
 */
class SpringLoggingAdapterTest {

    private final SpringLoggingAdapter adapter = new SpringLoggingAdapter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getSupportedFrameworkIsSpringBoot() {
        assertThat(adapter.getSupportedFramework()).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void getServiceReturnsSelf() {
        LoggingService service = adapter.getService();
        assertThat(service).isSameAs(adapter);
    }

    @Test
    void loggingMethodsDoNotThrow() {
        adapter.debug("debug");
        adapter.debug("debug {}", "a");
        adapter.info("info");
        adapter.info("info {}", "a");
        adapter.warn("warn");
        adapter.warn("warn {}", "a");
        adapter.error("error");
        adapter.error("error", new RuntimeException("x"));
        adapter.error("error {}", "a");
    }

    @Test
    void contextOperationsUseMdc() {
        adapter.addContext("k", "v");
        assertThat(adapter.getContext("k")).isEqualTo("v");

        adapter.addContexts(Map.of("a", "1", "b", "2"));
        assertThat(adapter.getContext("a")).isEqualTo("1");

        adapter.removeContext("k");
        assertThat(adapter.getContext("k")).isNull();

        adapter.clearContext();
        assertThat(adapter.getContext("a")).isNull();
    }
}
