package com.adhar.adharkit.metrics.integration;

import com.adhar.adharkit.metrics.annotation.ApiMetrics;
import com.adhar.adharkit.metrics.annotation.CacheMetrics;
import com.adhar.adharkit.metrics.annotation.Counted;
import com.adhar.adharkit.metrics.annotation.DatabaseMetrics;
import com.adhar.adharkit.metrics.annotation.MonitorPerformance;
import com.adhar.adharkit.metrics.annotation.Timed;
import com.adhar.adharkit.metrics.config.AdharMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete adhar-kit-metrics functionality.
 */
@SpringBootTest(classes = {
    AdharMetricsAutoConfiguration.class,
    MetricsIntegrationTest.TestService.class
})
@TestPropertySource(properties = {
    "adhar.metrics.enabled=true",
    "adhar.metrics.prometheus.enabled=true",
    "adhar.metrics.jvm.enabled=true",
    "adhar.metrics.system.enabled=true",
    "spring.application.name=metrics-integration-test"
})
class MetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestService testService;

    @Test
    void testTimedAnnotationIntegration() {
        // Execute method with @Timed annotation
        testService.timedMethod();

        // Verify timer was created and recorded
        assertThat(meterRegistry.find("test.timed.method").timer()).isNotNull();
        assertThat(meterRegistry.find("test.timed.method").timer().count()).isEqualTo(1);
    }

    @Test
    void testCountedAnnotationIntegration() {
        // Execute method with @Counted annotation multiple times
        testService.countedMethod();
        testService.countedMethod();
        testService.countedMethod();

        // Verify counter was created and incremented
        assertThat(meterRegistry.find("test.counted.method").counter()).isNotNull();
        assertThat(meterRegistry.find("test.counted.method").counter().count()).isEqualTo(3.0);
    }

    @Test
    void testMonitorPerformanceAnnotationIntegration() {
        // Execute method with @MonitorPerformance annotation
        String result = testService.monitoredMethod("test-input");

        // Verify performance metrics were recorded
        assertThat(result).isEqualTo("processed: test-input");
        assertThat(meterRegistry.find("test.monitored.method.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("test.monitored.method.calls").counter()).isNotNull();
    }

    @Test
    void testCacheMetricsAnnotationIntegration() {
        // Execute method with @CacheMetrics annotation
        testService.cacheMethod("key1");
        testService.cacheMethod("key1"); // Should be a cache hit
        testService.cacheMethod("key2"); // Should be a cache miss

        // Verify cache metrics were recorded
        assertThat(meterRegistry.find("test.cache.hits").counter()).isNotNull();
        assertThat(meterRegistry.find("test.cache.misses").counter()).isNotNull();
    }

    @Test
    void testDatabaseMetricsAnnotationIntegration() {
        // Execute method with @DatabaseMetrics annotation
        testService.databaseMethod("SELECT * FROM users");

        // Verify database metrics were recorded
        assertThat(meterRegistry.find("test.database.queries").timer()).isNotNull();
        assertThat(meterRegistry.find("test.database.connections").gauge()).isNotNull();
    }

    @Test
    void testApiMetricsAnnotationIntegration() {
        // Execute method with @ApiMetrics annotation
        testService.apiMethod("/api/users", "GET");

        // Verify API metrics were recorded
        assertThat(meterRegistry.find("test.api.requests").timer()).isNotNull();
        assertThat(meterRegistry.find("test.api.status").counter()).isNotNull();
    }

    @Test
    void testExceptionHandling() {
        // Test that exceptions are properly handled and metrics are still recorded
        try {
            testService.methodThatThrows();
        } catch (RuntimeException e) {
            // Expected exception
        }

        // Verify that error metrics were recorded
        assertThat(meterRegistry.find("test.error.method").counter()).isNotNull();
        assertThat(meterRegistry.find("test.error.method").tag("exception", "RuntimeException").counter()).isNotNull();
    }

    @Test
    void testCustomTagsIntegration() {
        // Execute method with custom tags
        testService.methodWithCustomTags("production", "v1.0.0");

        // Verify custom tags were applied
        assertThat(meterRegistry.find("test.custom.tags")
            .tag("environment", "production")
            .tag("version", "v1.0.0")
            .counter()).isNotNull();
    }

    @Test
    void testJvmMetricsEnabled() {
        // Verify JVM metrics are registered
        assertThat(meterRegistry.find("jvm.memory.used").meters()).isNotEmpty();
        assertThat(meterRegistry.find("jvm.threads.live").meters()).isNotEmpty();
        assertThat(meterRegistry.find("jvm.gc.pause").meters()).isNotEmpty();
    }

    @Test
    void testSystemMetricsEnabled() {
        // Verify system metrics are registered
        assertThat(meterRegistry.find("system.cpu.usage").meters()).isNotEmpty();
        assertThat(meterRegistry.find("process.uptime").meters()).isNotEmpty();
        assertThat(meterRegistry.find("system.load.average.1m").meters()).isNotEmpty();
    }

    @Test
    void testApplicationNameTagging() {
        // Verify that application name is added as a common tag
        testService.simpleMethod();

        assertThat(meterRegistry.find("test.simple")
            .tag("application", "metrics-integration-test")
            .counter()).isNotNull();
    }

    /**
     * Test service with various metrics annotations for integration testing.
     */
    @Service
    static class TestService {

        @Timed("test.timed.method")
        public void timedMethod() {
            // Simulate some work
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Counted("test.counted.method")
        public void countedMethod() {
            // Simple method for counting
        }

        @MonitorPerformance("test.monitored.method")
        public String monitoredMethod(String input) {
            return "processed: " + input;
        }

        @CacheMetrics("test.cache")
        public String cacheMethod(String key) {
            // Simulate cache behavior
            if ("key1".equals(key)) {
                return "cached-value-1";
            }
            return "new-value-" + key;
        }

        @DatabaseMetrics(value = "test.database", operation = "SELECT", table = "users")
        public void databaseMethod(String query) {
            // Simulate database operation
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @ApiMetrics("test.api")
        public String apiMethod(String path, String method) {
            // Simulate API call
            return "Response for " + method + " " + path;
        }

        @Counted(value = "test.error.method", recordFailuresOnly = true)
        public void methodThatThrows() {
            throw new RuntimeException("Test exception");
        }

        @Counted(value = "test.custom.tags", extraTags = {"environment", "#{#env}", "version", "#{#version}"})
        public void methodWithCustomTags(String env, String version) {
            // Method with custom tags
        }

        @Counted("test.simple")
        public void simpleMethod() {
            // Simple method for basic testing
        }
    }
}
