package com.adhar.kit.resilience.annotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreaker Annotation Tests")
class CircuitBreakerTest {

    @Test
    @DisplayName("Should have correct annotation properties")
    void testAnnotationProperties() {
        CircuitBreaker annotation = TestClass.class.getAnnotation(CircuitBreaker.class);

        assertNotNull(annotation);
        assertEquals("testCircuitBreaker", annotation.name());
        assertEquals("fallback", annotation.fallbackMethod());
    }

    @Test
    @DisplayName("Should use default values when not specified")
    void testDefaultValues() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("methodWithDefaults");
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);

        assertNotNull(annotation);
        assertEquals("default", annotation.name());
        assertEquals("", annotation.fallbackMethod());
    }

    @Test
    @DisplayName("Should be applicable to methods")
    void testMethodAnnotation() throws NoSuchMethodException {
        Method method = TestClass.class.getMethod("annotatedMethod");
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);

        assertNotNull(annotation);
        assertEquals("methodCircuitBreaker", annotation.name());
    }

    @Test
    @DisplayName("Should be runtime retained")
    void testRuntimeRetention() {
        CircuitBreaker annotation = TestClass.class.getAnnotation(CircuitBreaker.class);
        assertNotNull(annotation, "Annotation should be available at runtime");
    }

    @CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallback")
    static class TestClass {

        @CircuitBreaker(name = "methodCircuitBreaker", fallbackMethod = "handleError")
        public void annotatedMethod() {
        }

        @CircuitBreaker
        public void methodWithDefaults() {
        }
    }
}

