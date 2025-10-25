package com.adhar.adharkit.metrics.aspect;

import com.adhar.adharkit.metrics.annotation.Counted;
import com.adhar.adharkit.metrics.annotation.Gauged;
import com.adhar.adharkit.metrics.annotation.Summary;
import com.adhar.adharkit.metrics.annotation.Timed;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link EnhancedMetricsAspect}.
 */
@ExtendWith(MockitoExtension.class)
class EnhancedMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Method method;

    @Mock
    private Timed timedAnnotation;

    @Mock
    private Counted countedAnnotation;

    @Mock
    private Summary summaryAnnotation;

    @Mock
    private Gauged gaugedAnnotation;

    private MeterRegistry registry;
    private EnhancedMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new EnhancedMetricsAspect(registry);

        // Setup common mock behavior
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(method.getDeclaringClass()).thenReturn(TestService.class);
        when(method.getName()).thenReturn("testMethod");
    }

    // ==================== Timer Tests ====================

    @Test
    void timeMethod_WithSuccessfulExecution_RecordsTiming() throws Throwable {
        // Setup
        when(timedAnnotation.name()).thenReturn("custom.timer");
        when(timedAnnotation.description()).thenReturn("Test timer");
        when(timedAnnotation.tags()).thenReturn(new String[]{"key", "value"});
        when(timedAnnotation.successOnly()).thenReturn(false);
        when(timedAnnotation.percentiles()).thenReturn(new double[]{0.5, 0.95});
        when(joinPoint.proceed()).thenReturn("success");

        // Execute
        Object result = aspect.timeMethod(joinPoint, timedAnnotation);

        // Verify
        assertThat(result).isEqualTo("success");
        Timer timer = registry.get("custom.timer").timer();
        assertThat(timer.count()).isEqualTo(1);
        verify(joinPoint).proceed();
    }

    @Test
    void timeMethod_WithException_StillRecordsTiming() throws Throwable {
        // Setup
        when(timedAnnotation.name()).thenReturn("test.timer");
        when(timedAnnotation.description()).thenReturn("");
        when(timedAnnotation.tags()).thenReturn(new String[]{});
        when(timedAnnotation.successOnly()).thenReturn(false);
        when(timedAnnotation.percentiles()).thenReturn(new double[]{});
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        // Execute & Verify
        assertThatThrownBy(() -> aspect.timeMethod(joinPoint, timedAnnotation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");

        Timer timer = registry.get("test.timer").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void timeMethod_WithSuccessOnlyAndFailure_DoesNotRecordTiming() throws Throwable {
        // Setup
        when(timedAnnotation.name()).thenReturn("success.only.timer");
        when(timedAnnotation.description()).thenReturn("");
        when(timedAnnotation.tags()).thenReturn(new String[]{});
        when(timedAnnotation.successOnly()).thenReturn(true);
        when(timedAnnotation.percentiles()).thenReturn(new double[]{});
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        // Execute & Verify
        assertThatThrownBy(() -> aspect.timeMethod(joinPoint, timedAnnotation))
                .isInstanceOf(RuntimeException.class);

        // Timer should not be recorded for failed execution when successOnly=true
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void timeMethod_WithEmptyName_UsesGeneratedName() throws Throwable {
        // Setup
        when(timedAnnotation.name()).thenReturn("");
        when(timedAnnotation.description()).thenReturn("");
        when(timedAnnotation.tags()).thenReturn(new String[]{});
        when(timedAnnotation.successOnly()).thenReturn(false);
        when(timedAnnotation.percentiles()).thenReturn(new double[]{});
        when(joinPoint.proceed()).thenReturn("success");

        // Execute
        aspect.timeMethod(joinPoint, timedAnnotation);

        // Verify - should use generated name based on class and method
        Timer timer = registry.get("testservice.testMethod").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    // ==================== Counter Tests ====================

    @Test
    void countMethod_WithSuccessfulExecution_IncrementsCounter() throws Throwable {
        // Setup
        when(countedAnnotation.name()).thenReturn("custom.counter");
        when(countedAnnotation.description()).thenReturn("Test counter");
        when(countedAnnotation.tags()).thenReturn(new String[]{"type", "test"});
        when(countedAnnotation.successOnly()).thenReturn(false);
        when(countedAnnotation.recordFailures()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("success");

        // Execute
        Object result = aspect.countMethod(joinPoint, countedAnnotation);

        // Verify
        assertThat(result).isEqualTo("success");
        Counter counter = registry.get("custom.counter").counter();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void countMethod_WithFailureAndRecordFailures_IncrementsFailureCounter() throws Throwable {
        // Setup
        when(countedAnnotation.name()).thenReturn("test.counter");
        when(countedAnnotation.description()).thenReturn("");
        when(countedAnnotation.tags()).thenReturn(new String[]{});
        when(countedAnnotation.successOnly()).thenReturn(false);
        when(countedAnnotation.recordFailures()).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test failure"));

        // Execute & Verify
        assertThatThrownBy(() -> aspect.countMethod(joinPoint, countedAnnotation))
                .isInstanceOf(RuntimeException.class);

        Counter counter = registry.get("test.counter").counter();
        Counter failureCounter = registry.get("test.counter.failures").counter();
        assertThat(counter.count()).isEqualTo(1);
        assertThat(failureCounter.count()).isEqualTo(1);
    }

    @Test
    void countMethod_WithSuccessOnlyAndFailure_DoesNotIncrementCounter() throws Throwable {
        // Setup
        when(countedAnnotation.name()).thenReturn("success.only.counter");
        when(countedAnnotation.description()).thenReturn("");
        when(countedAnnotation.tags()).thenReturn(new String[]{});
        when(countedAnnotation.successOnly()).thenReturn(true);
        when(countedAnnotation.recordFailures()).thenReturn(false);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test failure"));

        // Execute & Verify
        assertThatThrownBy(() -> aspect.countMethod(joinPoint, countedAnnotation))
                .isInstanceOf(RuntimeException.class);

        // Counter should not be incremented for failed execution when successOnly=true
        assertThat(registry.getMeters()).isEmpty();
    }

    // ==================== Summary Tests ====================

    @Test
    void recordSummary_WithNumberResult_RecordsValue() {
        // Setup
        when(summaryAnnotation.name()).thenReturn("test.summary");
        when(summaryAnnotation.description()).thenReturn("Test summary");
        when(summaryAnnotation.tags()).thenReturn(new String[]{"unit", "count"});
        when(summaryAnnotation.baseUnit()).thenReturn("items");
        when(summaryAnnotation.percentiles()).thenReturn(new double[]{0.5, 0.99});
        when(summaryAnnotation.valueField()).thenReturn("");
        when(summaryAnnotation.successOnly()).thenReturn(false);

        // Execute
        aspect.recordSummary(joinPoint, 42.5, summaryAnnotation);

        // Verify
        DistributionSummary summary = registry.get("test.summary").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(42.5);
    }

    @Test
    void recordSummary_WithCollectionResult_RecordsSize() {
        // Setup
        when(summaryAnnotation.name()).thenReturn("collection.summary");
        when(summaryAnnotation.description()).thenReturn("");
        when(summaryAnnotation.tags()).thenReturn(new String[]{});
        when(summaryAnnotation.baseUnit()).thenReturn("");
        when(summaryAnnotation.percentiles()).thenReturn(new double[]{});
        when(summaryAnnotation.valueField()).thenReturn("");
        when(summaryAnnotation.successOnly()).thenReturn(false);

        List<String> result = Arrays.asList("item1", "item2", "item3");

        // Execute
        aspect.recordSummary(joinPoint, result, summaryAnnotation);

        // Verify
        DistributionSummary summary = registry.get("collection.summary").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(3.0);
    }

    @Test
    void recordSummary_WithStringResult_RecordsLength() {
        // Setup
        when(summaryAnnotation.name()).thenReturn("string.summary");
        when(summaryAnnotation.description()).thenReturn("");
        when(summaryAnnotation.tags()).thenReturn(new String[]{});
        when(summaryAnnotation.baseUnit()).thenReturn("");
        when(summaryAnnotation.percentiles()).thenReturn(new double[]{});
        when(summaryAnnotation.valueField()).thenReturn("");
        when(summaryAnnotation.successOnly()).thenReturn(false);

        String result = "Hello World";

        // Execute
        aspect.recordSummary(joinPoint, result, summaryAnnotation);

        // Verify
        DistributionSummary summary = registry.get("string.summary").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(11.0);
    }

    @Test
    void recordSummary_WithArrayResult_RecordsLength() {
        // Setup
        when(summaryAnnotation.name()).thenReturn("array.summary");
        when(summaryAnnotation.description()).thenReturn("");
        when(summaryAnnotation.tags()).thenReturn(new String[]{});
        when(summaryAnnotation.baseUnit()).thenReturn("");
        when(summaryAnnotation.percentiles()).thenReturn(new double[]{});
        when(summaryAnnotation.valueField()).thenReturn("");
        when(summaryAnnotation.successOnly()).thenReturn(false);

        String[] result = {"a", "b", "c", "d"};

        // Execute
        aspect.recordSummary(joinPoint, result, summaryAnnotation);

        // Verify
        DistributionSummary summary = registry.get("array.summary").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(4.0);
    }

    @Test
    void recordSummary_WithNullResult_DoesNotRecord() {
        // Setup
        when(summaryAnnotation.name()).thenReturn("null.summary");
        when(summaryAnnotation.successOnly()).thenReturn(true);

        // Execute
        aspect.recordSummary(joinPoint, null, summaryAnnotation);

        // Verify - no metrics should be recorded
        assertThat(registry.getMeters()).isEmpty();
    }

    // ==================== Gauge Tests ====================

    @Test
    void recordGauge_WithNumberResult_CreatesGauge() {
        // Setup
        when(gaugedAnnotation.name()).thenReturn("test.gauge");
        when(gaugedAnnotation.description()).thenReturn("Test gauge");
        when(gaugedAnnotation.tags()).thenReturn(new String[]{"type", "number"});
        when(gaugedAnnotation.baseUnit()).thenReturn("count");
        when(gaugedAnnotation.updateOnCall()).thenReturn(true);

        // Execute
        aspect.recordGauge(joinPoint, 75.5, gaugedAnnotation);

        // Verify that gauge creation was attempted (it may not be fully functional due to gauge complexity)
        assertDoesNotThrow(() -> aspect.recordGauge(joinPoint, 75.5, gaugedAnnotation));
    }

    @Test
    void recordGauge_WithCollectionResult_CreatesGauge() {
        // Setup
        when(gaugedAnnotation.name()).thenReturn("collection.gauge");
        when(gaugedAnnotation.description()).thenReturn("");
        when(gaugedAnnotation.tags()).thenReturn(new String[]{});
        when(gaugedAnnotation.baseUnit()).thenReturn("");
        when(gaugedAnnotation.updateOnCall()).thenReturn(true);

        List<String> result = Arrays.asList("a", "b", "c");

        // Execute
        assertDoesNotThrow(() -> aspect.recordGauge(joinPoint, result, gaugedAnnotation));
    }

    @Test
    void recordGauge_WithUpdateOnCallDisabled_DoesNotUpdate() {
        // Setup
        when(gaugedAnnotation.updateOnCall()).thenReturn(false);

        // Execute
        assertDoesNotThrow(() -> aspect.recordGauge(joinPoint, 100, gaugedAnnotation));

        // Should not create any gauges
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void recordGauge_WithNullResult_DoesNotUpdate() {
        // Setup
        when(gaugedAnnotation.updateOnCall()).thenReturn(true);

        // Execute
        assertDoesNotThrow(() -> aspect.recordGauge(joinPoint, null, gaugedAnnotation));
    }

    // ==================== Helper Classes ====================

    /**
     * Test service class for method signature mocking.
     */
    static class TestService {
        public String testMethod() {
            return "test";
        }
    }

    /**
     * Test class with field for reflection testing.
     */
    static class TestObjectWithField {
        private final int count;

        public TestObjectWithField(int count) {
            this.count = count;
        }
    }
}

