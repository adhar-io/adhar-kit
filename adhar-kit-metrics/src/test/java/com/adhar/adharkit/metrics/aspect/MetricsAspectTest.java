package com.adhar.adharkit.metrics.aspect;

import com.adhar.adharkit.metrics.annotation.Timed;
import com.adhar.adharkit.metrics.util.MetricsUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MetricsAspect}.
 */
@ExtendWith(MockitoExtension.class)
class MetricsAspectTest {

    @Mock
    private MeterRegistry registry;

    @Mock
    private MetricsUtils metricsUtils;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Sample sample;

    private MetricsAspect aspect;
    private TestService testService;
    private TestService proxiedService;

    @BeforeEach
    void setUp() {
        aspect = new MetricsAspect(registry, metricsUtils);
        testService = new TestService();
        
        // Create a proxy with the aspect
        AspectJProxyFactory factory = new AspectJProxyFactory(testService);
        factory.addAspect(aspect);
        proxiedService = factory.getProxy();
        
        // Setup mocks
        when(Timer.start(registry)).thenReturn(sample);
        when(metricsUtils.timer(anyString(), any(String[].class))).thenReturn(timer);
    }

    @Test
    void testTimedMethodWithDefaultName() {
        // Execute
        String result = proxiedService.methodWithDefaultName("test");
        
        // Verify
        assertThat(result).isEqualTo("test");
        verify(Timer.class, times(1)).start(registry);
        verify(metricsUtils, times(1)).timer(eq("TestService.methodWithDefaultName"), 
                eq(new String[]{"class", "TestService", "method", "methodWithDefaultName"}));
        verify(sample, times(1)).stop(timer);
    }

    @Test
    void testTimedMethodWithCustomName() {
        // Execute
        String result = proxiedService.methodWithCustomName("test");
        
        // Verify
        assertThat(result).isEqualTo("test");
        verify(Timer.class, times(1)).start(registry);
        verify(metricsUtils, times(1)).timer(eq("custom.metric.name"), 
                eq(new String[]{"class", "TestService", "method", "methodWithCustomName"}));
        verify(sample, times(1)).stop(timer);
    }

    @Test
    void testTimedMethodWithCustomTags() {
        // Execute
        String result = proxiedService.methodWithCustomTags("test");
        
        // Verify
        assertThat(result).isEqualTo("test");
        verify(Timer.class, times(1)).start(registry);
        verify(metricsUtils, times(1)).timer(eq("TestService.methodWithCustomTags"), 
                eq(new String[]{"tag1", "value1", "tag2", "value2"}));
        verify(sample, times(1)).stop(timer);
    }

    @Test
    void testTimedMethodWithException() {
        // Setup
        when(metricsUtils.timer(eq("TestService.methodWithException"), 
                eq(new String[]{"class", "TestService", "method", "methodWithException", "exception", "RuntimeException"})))
                .thenReturn(timer);
        
        // Execute and verify
        assertThatThrownBy(() -> proxiedService.methodWithException())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");
        
        verify(Timer.class, times(1)).start(registry);
        verify(metricsUtils, times(1)).timer(eq("TestService.methodWithException"), 
                eq(new String[]{"class", "TestService", "method", "methodWithException"}));
        verify(metricsUtils, times(1)).timer(eq("TestService.methodWithException"), 
                eq(new String[]{"class", "TestService", "method", "methodWithException", "exception", "RuntimeException"}));
        verify(sample, times(2)).stop(timer); // Once for the exception, once in finally
    }

    /**
     * Test service class with methods annotated with {@link Timed}.
     */
    static class TestService {
        
        @Timed
        public String methodWithDefaultName(String input) {
            return input;
        }
        
        @Timed("custom.metric.name")
        public String methodWithCustomName(String input) {
            return input;
        }
        
        @Timed(tags = {"tag1", "value1", "tag2", "value2"})
        public String methodWithCustomTags(String input) {
            return input;
        }
        
        @Timed
        public void methodWithException() {
            throw new RuntimeException("Test exception");
        }
    }
}