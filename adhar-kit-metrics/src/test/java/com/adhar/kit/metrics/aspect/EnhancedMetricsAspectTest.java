package com.adhar.kit.metrics.aspect;

import com.adhar.kit.metrics.annotation.ApiMetrics;
import com.adhar.kit.metrics.annotation.CacheMetrics;
import com.adhar.kit.metrics.annotation.Counted;
import com.adhar.kit.metrics.annotation.DatabaseMetrics;
import com.adhar.kit.metrics.annotation.Gauged;
import com.adhar.kit.metrics.annotation.Histogram;
import com.adhar.kit.metrics.annotation.MonitorPerformance;
import com.adhar.kit.metrics.annotation.Summary;
import com.adhar.kit.metrics.annotation.Timed;
import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.util.AdharMetrics;
import com.adhar.kit.metrics.util.MetricsUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavioural unit tests for {@link EnhancedMetricsAspect}.
 * <p>
 * Each advice method is invoked directly with a Mockito-mocked
 * {@link ProceedingJoinPoint} backed by a real annotated method from {@link Sample},
 * so annotation attribute defaults are exercised exactly as at runtime. A real
 * {@link SimpleMeterRegistry} verifies that the expected meters are produced.
 * </p>
 */
class EnhancedMetricsAspectTest {

    private SimpleMeterRegistry registry;
    private EnhancedMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        AdharMetricsProperties props = new AdharMetricsProperties();
        // The DB/API/cache handlers delegate to the static AdharMetrics facade.
        AdharMetrics.initialize(registry, props, new MetricsUtils(registry, props), null);
        AdharMetrics.clearCache();
        aspect = new EnhancedMetricsAspect(registry);
    }

    // ---- helpers -----------------------------------------------------------

    private ProceedingJoinPoint jp(String methodName, Object proceedResult) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method m = method(methodName);
        lenient().when(sig.getMethod()).thenReturn(m);
        lenient().when(pjp.getSignature()).thenReturn(sig);
        try {
            lenient().when(pjp.proceed()).thenReturn(proceedResult);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        return pjp;
    }

    private ProceedingJoinPoint jpThrow(String methodName, Throwable toThrow) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method m = method(methodName);
        lenient().when(sig.getMethod()).thenReturn(m);
        lenient().when(pjp.getSignature()).thenReturn(sig);
        try {
            lenient().when(pjp.proceed()).thenThrow(toThrow);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        return pjp;
    }

    private static Method method(String name) {
        for (Method m : Sample.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("no method " + name);
    }

    private static <A extends java.lang.annotation.Annotation> A ann(String methodName, Class<A> type) {
        return method(methodName).getAnnotation(type);
    }

    // ==================== @Timed ====================

    @Test
    void timeMethod_namedTimer_recordsSample() throws Throwable {
        Object result = aspect.timeMethod(jp("timedNamed", "ok"), ann("timedNamed", Timed.class));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("timed.named").timer().count()).isEqualTo(1L);
    }

    @Test
    void timeMethod_successOnly_derivesNameFromSignature() throws Throwable {
        aspect.timeMethod(jp("timedSuccessOnly", "ok"), ann("timedSuccessOnly", Timed.class));

        assertThat(registry.find("sample.timedSuccessOnly").timer().count()).isEqualTo(1L);
    }

    @Test
    void timeMethod_exception_stillRecordsWhenNotSuccessOnly() {
        ProceedingJoinPoint pjp = jpThrow("timedPlain", new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.timeMethod(pjp, ann("timedPlain", Timed.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find("sample.timedPlain").timer().count()).isEqualTo(1L);
    }

    @Test
    void timeMethod_successOnly_exceptionSkipsRecording() {
        ProceedingJoinPoint pjp = jpThrow("timedSuccessOnly", new RuntimeException("x"));

        assertThatThrownBy(() -> aspect.timeMethod(pjp, ann("timedSuccessOnly", Timed.class)))
                .isInstanceOf(RuntimeException.class);
        // The timer is registered eagerly but the sample is never stopped on failure.
        assertThat(registry.find("sample.timedSuccessOnly").timer().count()).isEqualTo(0L);
    }

    // ==================== @Counted ====================

    @Test
    void countMethod_success_incrementsCounter() throws Throwable {
        aspect.countMethod(jp("countedNamed", "ok"), ann("countedNamed", Counted.class));

        assertThat(registry.find("counted.named").counter().count()).isEqualTo(1.0);
        // recordFailures creates the failure counter eagerly but it stays at zero
        assertThat(registry.find("counted.named.failures").counter().count()).isEqualTo(0.0);
    }

    @Test
    void countMethod_exception_incrementsFailureAndTotal() {
        ProceedingJoinPoint pjp = jpThrow("countedFailures", new IllegalArgumentException("x"));

        assertThatThrownBy(() -> aspect.countMethod(pjp, ann("countedFailures", Counted.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.find("sample.countedFailures.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("sample.countedFailures").counter().count()).isEqualTo(1.0);
    }

    @Test
    void countMethod_successOnly_exceptionDoesNotIncrement() {
        ProceedingJoinPoint pjp = jpThrow("countedSuccessOnly", new RuntimeException("x"));

        assertThatThrownBy(() -> aspect.countMethod(pjp, ann("countedSuccessOnly", Counted.class)))
                .isInstanceOf(RuntimeException.class);
        assertThat(registry.find("sample.countedSuccessOnly").counter().count()).isEqualTo(0.0);
    }

    // ==================== @Summary ====================

    @Test
    void recordSummary_numberResult_records() {
        aspect.recordSummary(jp("summaryNamed", 5), 5, ann("summaryNamed", Summary.class));

        assertThat(registry.find("summary.named").summary().count()).isEqualTo(1L);
        assertThat(registry.find("summary.named").summary().totalAmount()).isEqualTo(5.0);
    }

    @Test
    void recordSummary_variousResultTypes_extractValue() {
        Summary a = ann("summaryNamed", Summary.class);
        aspect.recordSummary(jp("summaryNamed", null), List.of(1, 2, 3), a); // collection -> 3
        aspect.recordSummary(jp("summaryNamed", null), "hello", a);          // string -> 5
        aspect.recordSummary(jp("summaryNamed", null), new Object[]{1, 2}, a); // array -> 2

        assertThat(registry.find("summary.named").summary().count()).isEqualTo(3L);
        assertThat(registry.find("summary.named").summary().totalAmount()).isEqualTo(10.0);
    }

    @Test
    void recordSummary_successOnlyWithNull_skips() {
        aspect.recordSummary(jp("summarySuccessOnly", null), null, ann("summarySuccessOnly", Summary.class));

        assertThat(registry.find("sample.summarySuccessOnly").summary()).isNull();
    }

    @Test
    void recordSummary_nullResultNaN_skipsRecording() {
        aspect.recordSummary(jp("summaryNamed", null), null, ann("summaryNamed", Summary.class));

        // Summary is registered but a NaN value is never recorded.
        assertThat(registry.find("summary.named").summary().count()).isEqualTo(0L);
    }

    @Test
    void recordSummary_valueFieldExtraction() {
        aspect.recordSummary(jp("summaryField", null), new Pojo(), ann("summaryField", Summary.class));

        assertThat(registry.find("sample.summaryField").summary().totalAmount()).isEqualTo(42.0);
    }

    @Test
    void recordSummary_missingValueField_skips() {
        aspect.recordSummary(jp("summaryBadField", null), new Pojo(), ann("summaryBadField", Summary.class));

        // Field extraction fails -> NaN -> nothing recorded though the summary exists.
        assertThat(registry.find("sample.summaryBadField").summary().count()).isEqualTo(0L);
    }

    // ==================== @Gauged ====================

    @Test
    void recordGauge_registersGaugeAndIsCachedOnSecondCall() {
        Gauged a = ann("gaugedNamed", Gauged.class);
        aspect.recordGauge(jp("gaugedNamed", 10), 10, a);
        assertThat(registry.find("gauged.named").gauge()).isNotNull();

        // second call hits the gauge cache branch and exercises collection extraction
        aspect.recordGauge(jp("gaugedNamed", List.of(1, 2)), List.of(1, 2), a);
        aspect.recordGauge(jp("gaugedNamed", new Object[]{1}), new Object[]{1}, a);

        assertThat(registry.find("gauged.named").gauges()).hasSize(1);
    }

    @Test
    void recordGauge_nullResult_skips() {
        aspect.recordGauge(jp("gaugedPlain", null), null, ann("gaugedPlain", Gauged.class));

        assertThat(registry.find("sample.gaugedPlain").gauge()).isNull();
    }

    // ==================== @Histogram ====================

    @Test
    void recordHistogram_timingMode_recordsTimer() throws Throwable {
        Object result = aspect.recordHistogram(jp("histTiming", "ok"), ann("histTiming", Histogram.class));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("hist.timing").timer().count()).isEqualTo(1L);
    }

    @Test
    void recordHistogram_timingMode_exceptionStillRecords() {
        ProceedingJoinPoint pjp = jpThrow("histTiming", new RuntimeException("x"));

        assertThatThrownBy(() -> aspect.recordHistogram(pjp, ann("histTiming", Histogram.class)))
                .isInstanceOf(RuntimeException.class);
        assertThat(registry.find("hist.timing").timer().count()).isEqualTo(1L);
    }

    @Test
    void recordHistogram_valueMode_recordsSummary() throws Throwable {
        Object result = aspect.recordHistogram(jp("histValue", 7), ann("histValue", Histogram.class));

        assertThat(result).isEqualTo(7);
        assertThat(registry.find("hist.value").summary().totalAmount()).isEqualTo(7.0);
    }

    @Test
    void recordHistogram_valueModeSuccessOnly_nullSkips() throws Throwable {
        Object result = aspect.recordHistogram(jp("histValueSuccessOnly", null),
                ann("histValueSuccessOnly", Histogram.class));

        assertThat(result).isNull();
        assertThat(registry.find("sample.histValueSuccessOnly").summary()).isNull();
    }

    // ==================== @MonitorPerformance ====================

    @Test
    void monitorPerformance_success_recordsDurationAndThroughput() throws Throwable {
        Object result = aspect.monitorPerformance(jp("perfNamed", "ok"),
                ann("perfNamed", MonitorPerformance.class));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("perf.named.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.find("perf.named.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorPerformance_runtimeException_incrementsErrorCounter() {
        ProceedingJoinPoint pjp = jpThrow("perfNamed", new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.monitorPerformance(pjp, ann("perfNamed", MonitorPerformance.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find("perf.named.errors").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorPerformance_checkedException_isWrapped() {
        ProceedingJoinPoint pjp = jpThrow("perfNamed", new IOException("io"));

        assertThatThrownBy(() -> aspect.monitorPerformance(pjp, ann("perfNamed", MonitorPerformance.class)))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThat(registry.find("perf.named.errors").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorPerformance_minimalConfig_onlyTimer() throws Throwable {
        aspect.monitorPerformance(jp("perfMinimal", "ok"), ann("perfMinimal", MonitorPerformance.class));

        assertThat(registry.find("perf.min.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.find("perf.min.total").counter()).isNull();
        assertThat(registry.find("perf.min.errors").counter()).isNull();
    }

    // ==================== @CacheMetrics ====================

    @Test
    void monitorCache_hitWithLoadTime_recordsHitAndLoadTimer() throws Throwable {
        Object result = aspect.monitorCache(jp("cacheNamed", "ok"), ann("cacheNamed", CacheMetrics.class));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("cache.hits").tag("cache", "mycache").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("cache.requests").tag("cache", "mycache").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("cache.load.time").timer().count()).isEqualTo(1L);
    }

    @Test
    void monitorCache_hitIndicatorMatch_recordsHit() throws Throwable {
        aspect.monitorCache(jp("cacheHitIndicator", "HIT"), ann("cacheHitIndicator", CacheMetrics.class));

        assertThat(registry.find("cache.hits").tag("cache", "cache.hit").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorCache_nullIsMiss_recordsMiss() throws Throwable {
        aspect.monitorCache(jp("cacheNull", null), ann("cacheNull", CacheMetrics.class));

        assertThat(registry.find("cache.misses").tag("cache", "cache.null").counter().count()).isEqualTo(1.0);
    }

    // ==================== @DatabaseMetrics ====================

    @Test
    void monitorDatabase_collectionResult_recordsRowCountAndTimer() throws Throwable {
        Object result = aspect.monitorDatabase(jp("dbSelect", List.of("a", "b")),
                ann("dbSelect", DatabaseMetrics.class));

        assertThat(result).isEqualTo(List.of("a", "b"));
        assertThat(registry.find("database.rows").summary().totalAmount()).isEqualTo(2.0);
        assertThat(registry.find("database.query.time").timer().count()).isEqualTo(1L);
    }

    @Test
    void monitorDatabase_numberResultSlowQuery_recordsSlowQueryAlert() throws Throwable {
        aspect.monitorDatabase(jp("dbSlow", 3), ann("dbSlow", DatabaseMetrics.class));

        assertThat(registry.find("database.rows").summary().totalAmount()).isEqualTo(3.0);
        assertThat(registry.find("database.slow_queries").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorDatabase_arrayResult_recordsRowCount() throws Throwable {
        aspect.monitorDatabase(jp("dbArray", new Object[]{1, 2, 3, 4}),
                ann("dbArray", DatabaseMetrics.class));

        assertThat(registry.find("database.rows").summary().totalAmount()).isEqualTo(4.0);
    }

    @Test
    void monitorDatabase_exception_recordsError() {
        ProceedingJoinPoint pjp = jpThrow("dbError", new RuntimeException("db down"));

        assertThatThrownBy(() -> aspect.monitorDatabase(pjp, ann("dbError", DatabaseMetrics.class)))
                .isInstanceOf(RuntimeException.class);
        assertThat(registry.find("database.errors").counter().count()).isEqualTo(1.0);
    }

    // ==================== @ApiMetrics ====================

    @Test
    void monitorApi_stringResult_recordsPayloadAndRequest() throws Throwable {
        Object result = aspect.monitorApi(jp("apiNamed", "response"), ann("apiNamed", ApiMetrics.class));

        assertThat(result).isEqualTo("response");
        assertThat(registry.find("api.request.time").timer().count()).isEqualTo(1L);
        assertThat(registry.find("api.payload.size").summary().count()).isEqualTo(1L);
        assertThat(registry.find("api.requests").tag("status", "200").counter().count()).isEqualTo(1.0);
    }

    @Test
    void monitorApi_collectionResult_recordsPayloadSize() throws Throwable {
        aspect.monitorApi(jp("apiCollection", List.of("x", "y", "z")),
                ann("apiCollection", ApiMetrics.class));

        assertThat(registry.find("api.payload.size").summary().totalAmount()).isEqualTo(3.0);
    }

    @Test
    void monitorApi_exception_recordsErrorStatus() {
        ProceedingJoinPoint pjp = jpThrow("apiPlain", new RuntimeException("boom"));

        assertThatThrownBy(() -> aspect.monitorApi(pjp, ann("apiPlain", ApiMetrics.class)))
                .isInstanceOf(RuntimeException.class);
        assertThat(registry.find("api.requests").tag("status", "500").counter().count()).isEqualTo(1.0);
    }

    // ==================== Sample annotated methods ====================

    @SuppressWarnings("unused")
    static class Sample {

        @Timed(name = "timed.named", description = "desc", percentiles = {0.5, 0.95})
        public String timedNamed() {
            return "ok";
        }

        @Timed(successOnly = true)
        public String timedSuccessOnly() {
            return "ok";
        }

        @Timed
        public String timedPlain() {
            return "ok";
        }

        @Counted(name = "counted.named", description = "desc", recordFailures = true)
        public String countedNamed() {
            return "ok";
        }

        @Counted(recordFailures = true)
        public String countedFailures() {
            return "ok";
        }

        @Counted(successOnly = true)
        public String countedSuccessOnly() {
            return "ok";
        }

        @Summary(name = "summary.named", description = "desc", percentiles = {0.5})
        public Integer summaryNamed() {
            return 5;
        }

        @Summary(successOnly = true)
        public Integer summarySuccessOnly() {
            return 5;
        }

        @Summary(valueField = "amount")
        public Object summaryField() {
            return new Pojo();
        }

        @Summary(valueField = "doesNotExist")
        public Object summaryBadField() {
            return new Pojo();
        }

        @Gauged(name = "gauged.named", description = "desc", tags = {"k", "v"})
        public Integer gaugedNamed() {
            return 10;
        }

        @Gauged
        public Integer gaugedPlain() {
            return 10;
        }

        @Histogram(name = "hist.timing")
        public String histTiming() {
            return "ok";
        }

        @Histogram(name = "hist.value", recordTiming = false, description = "d")
        public Integer histValue() {
            return 7;
        }

        @Histogram(recordTiming = false, successOnly = true)
        public Integer histValueSuccessOnly() {
            return 7;
        }

        @MonitorPerformance(name = "perf.named")
        public String perfNamed() {
            return "ok";
        }

        @MonitorPerformance(name = "perf.min", recordThroughput = false,
                recordErrorRate = false, recordPercentiles = false)
        public String perfMinimal() {
            return "ok";
        }

        @CacheMetrics(cacheName = "mycache", recordLoadTime = true)
        public String cacheNamed() {
            return "ok";
        }

        @CacheMetrics(name = "cache.hit", hitIndicator = "HIT")
        public String cacheHitIndicator() {
            return "HIT";
        }

        @CacheMetrics(name = "cache.null")
        public String cacheNull() {
            return null;
        }

        @DatabaseMetrics(operation = "SELECT", table = "users", dataSource = "primary")
        public List<String> dbSelect() {
            return List.of("a", "b");
        }

        @DatabaseMetrics(alertOnSlowQuery = true, slowQueryThresholdMs = -1)
        public Integer dbSlow() {
            return 3;
        }

        @DatabaseMetrics
        public Object[] dbArray() {
            return new Object[]{1, 2, 3, 4};
        }

        @DatabaseMetrics
        public String dbError() {
            return "ok";
        }

        @ApiMetrics(endpoint = "/api", method = "GET", version = "v2")
        public String apiNamed() {
            return "response";
        }

        @ApiMetrics(endpoint = "/list", method = "GET")
        public List<String> apiCollection() {
            return List.of("x", "y", "z");
        }

        @ApiMetrics
        public String apiPlain() {
            return "response";
        }
    }

    @SuppressWarnings("unused")
    static class Pojo {
        private final int amount = 42;
    }
}
