package com.adhar.kit.resilience.annotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeLimiter Annotation Tests")
class TimeLimiterTest {

    @Test
    @DisplayName("Should have correct annotation properties")
    void testAnnotationProperties() {
        TimeLimiter annotation = TestClass.class.getAnnotation(TimeLimiter.class);

        assertNotNull(annotation);
        assertEquals("testTimeLimiter", annotation.name());
        assertEquals("handleTimeout", annotation.fallbackMethod());
        assertEquals(5000L, annotation.timeoutDuration());
        assertFalse(annotation.cancelRunningFuture());
    }

    @Test
    @DisplayName("Should use default values")
    void testDefaultValues() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("methodWithDefaults");
        TimeLimiter annotation = method.getAnnotation(TimeLimiter.class);

        assertNotNull(annotation);
        assertEquals("default", annotation.name());
        assertEquals("", annotation.fallbackMethod());
        assertEquals(-1L, annotation.timeoutDuration()); // -1 = use registry default config
        assertTrue(annotation.cancelRunningFuture());
    }

    @Test
    @DisplayName("Should be applicable to methods")
    void testMethodAnnotation() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("annotatedMethod");
        TimeLimiter annotation = method.getAnnotation(TimeLimiter.class);

        assertNotNull(annotation);
        assertEquals("methodTimeLimiter", annotation.name());
        assertEquals(2000L, annotation.timeoutDuration());
    }

    @Test
    @DisplayName("Should be runtime retained")
    void testRuntimeRetention() {
        TimeLimiter annotation = TestClass.class.getAnnotation(TimeLimiter.class);
        assertNotNull(annotation, "Annotation should be available at runtime");
    }

    @TimeLimiter(name = "testTimeLimiter", fallbackMethod = "handleTimeout",
                 timeoutDuration = 5000L, cancelRunningFuture = false)
    static class TestClass {

        @TimeLimiter(name = "methodTimeLimiter", timeoutDuration = 2000L)
        public void annotatedMethod() {
        }

        @TimeLimiter
        public void methodWithDefaults() {
        }
    }
}

