package com.adhar.adharkit.metrics.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for metrics annotations to ensure they are properly configured.
 */
class AnnotationTest {

    @Test
    void testTimedAnnotation() {
        // Verify that @Timed annotation has correct defaults
        Timed timed = TestClass.class.getMethod("timedMethod").getAnnotation(Timed.class);

        assertThat(timed).isNotNull();
        assertThat(timed.value()).isEmpty();
        assertThat(timed.description()).isEmpty();
        assertThat(timed.extraTags()).isEmpty();
    }

    @Test
    void testCountedAnnotation() throws NoSuchMethodException {
        // Verify that @Counted annotation has correct defaults
        Counted counted = TestClass.class.getMethod("countedMethod").getAnnotation(Counted.class);

        assertThat(counted).isNotNull();
        assertThat(counted.value()).isEmpty();
        assertThat(counted.description()).isEmpty();
        assertThat(counted.extraTags()).isEmpty();
        assertThat(counted.recordFailuresOnly()).isFalse();
    }

    @Test
    void testGaugedAnnotation() throws NoSuchMethodException {
        // Verify that @Gauged annotation has correct defaults
        Gauged gauged = TestClass.class.getMethod("gaugedMethod").getAnnotation(Gauged.class);

        assertThat(gauged).isNotNull();
        assertThat(gauged.value()).isEmpty();
        assertThat(gauged.description()).isEmpty();
        assertThat(gauged.extraTags()).isEmpty();
    }

    @Test
    void testHistogramAnnotation() throws NoSuchMethodException {
        // Verify that @Histogram annotation has correct defaults
        Histogram histogram = TestClass.class.getMethod("histogramMethod").getAnnotation(Histogram.class);

        assertThat(histogram).isNotNull();
        assertThat(histogram.value()).isEmpty();
        assertThat(histogram.description()).isEmpty();
        assertThat(histogram.extraTags()).isEmpty();
        assertThat(histogram.buckets()).isEmpty();
    }

    @Test
    void testSummaryAnnotation() throws NoSuchMethodException {
        // Verify that @Summary annotation has correct defaults
        Summary summary = TestClass.class.getMethod("summaryMethod").getAnnotation(Summary.class);

        assertThat(summary).isNotNull();
        assertThat(summary.value()).isEmpty();
        assertThat(summary.description()).isEmpty();
        assertThat(summary.extraTags()).isEmpty();
        assertThat(summary.quantiles()).isEmpty();
    }

    @Test
    void testMonitorPerformanceAnnotation() throws NoSuchMethodException {
        // Verify that @MonitorPerformance annotation has correct defaults
        MonitorPerformance monitor = TestClass.class.getMethod("monitoredMethod").getAnnotation(MonitorPerformance.class);

        assertThat(monitor).isNotNull();
        assertThat(monitor.value()).isEmpty();
        assertThat(monitor.includeExceptions()).isTrue();
        assertThat(monitor.includeArgs()).isFalse();
        assertThat(monitor.includeResult()).isFalse();
    }

    @Test
    void testCacheMetricsAnnotation() throws NoSuchMethodException {
        // Verify that @CacheMetrics annotation has correct defaults
        CacheMetrics cache = TestClass.class.getMethod("cacheMethod").getAnnotation(CacheMetrics.class);

        assertThat(cache).isNotNull();
        assertThat(cache.value()).isEmpty();
        assertThat(cache.cacheName()).isEmpty();
    }

    @Test
    void testDatabaseMetricsAnnotation() throws NoSuchMethodException {
        // Verify that @DatabaseMetrics annotation has correct defaults
        DatabaseMetrics db = TestClass.class.getMethod("databaseMethod").getAnnotation(DatabaseMetrics.class);

        assertThat(db).isNotNull();
        assertThat(db.value()).isEmpty();
        assertThat(db.operation()).isEmpty();
        assertThat(db.table()).isEmpty();
    }

    @Test
    void testApiMetricsAnnotation() throws NoSuchMethodException {
        // Verify that @ApiMetrics annotation has correct defaults
        ApiMetrics api = TestClass.class.getMethod("apiMethod").getAnnotation(ApiMetrics.class);

        assertThat(api).isNotNull();
        assertThat(api.value()).isEmpty();
        assertThat(api.path()).isEmpty();
        assertThat(api.method()).isEmpty();
    }

    /**
     * Test class with annotated methods for testing annotations.
     */
    static class TestClass {

        @Timed
        public void timedMethod() {}

        @Counted
        public void countedMethod() {}

        @Gauged
        public void gaugedMethod() {}

        @Histogram
        public void histogramMethod() {}

        @Summary
        public void summaryMethod() {}

        @MonitorPerformance
        public void monitoredMethod() {}

        @CacheMetrics
        public void cacheMethod() {}

        @DatabaseMetrics
        public void databaseMethod() {}

        @ApiMetrics
        public void apiMethod() {}
    }
}
