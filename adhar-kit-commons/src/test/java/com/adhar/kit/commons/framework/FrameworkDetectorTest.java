package com.adhar.kit.commons.framework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkDetectorTest {

    @AfterEach
    void tearDown() {
        FrameworkDetector.resetDetection();
    }

    @Test
    void detect_shouldReturnSpringBootWhenSpringBootIsOnClasspath() {
        // Spring Boot is on the classpath in this test environment
        Framework detected = FrameworkDetector.detect();
        assertThat(detected).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void detect_shouldCacheResult() {
        Framework first = FrameworkDetector.detect();
        Framework second = FrameworkDetector.detect();
        assertThat(first).isSameAs(second);
    }

    @Test
    void isSpringBoot_shouldReturnTrueWhenSpringBootDetected() {
        assertThat(FrameworkDetector.isSpringBoot()).isTrue();
    }

    @Test
    void isQuarkus_shouldReturnFalseWhenSpringBootDetected() {
        assertThat(FrameworkDetector.isQuarkus()).isFalse();
    }

    @Test
    void isMicronaut_shouldReturnFalseWhenSpringBootDetected() {
        assertThat(FrameworkDetector.isMicronaut()).isFalse();
    }

    @Test
    void isHelidon_shouldReturnFalseWhenSpringBootDetected() {
        assertThat(FrameworkDetector.isHelidon()).isFalse();
    }

    @Test
    void isVertx_shouldReturnFalseWhenSpringBootDetected() {
        assertThat(FrameworkDetector.isVertx()).isFalse();
    }

    @Test
    void forceFramework_shouldOverrideDetection() {
        FrameworkDetector.forceFramework(Framework.QUARKUS);
        assertThat(FrameworkDetector.detect()).isEqualTo(Framework.QUARKUS);
        assertThat(FrameworkDetector.isQuarkus()).isTrue();
        assertThat(FrameworkDetector.isSpringBoot()).isFalse();
    }

    @Test
    void resetDetection_shouldClearCachedValue() {
        FrameworkDetector.detect(); // cache a value
        FrameworkDetector.resetDetection();
        // After reset, detect should re-evaluate
        Framework detected = FrameworkDetector.detect();
        assertThat(detected).isNotNull();
    }

    @Test
    void forceFramework_toOther_shouldReportNoFramework() {
        FrameworkDetector.forceFramework(Framework.OTHER);
        assertThat(FrameworkDetector.isSpringBoot()).isFalse();
        assertThat(FrameworkDetector.isQuarkus()).isFalse();
        assertThat(FrameworkDetector.isMicronaut()).isFalse();
        assertThat(FrameworkDetector.isHelidon()).isFalse();
        assertThat(FrameworkDetector.isVertx()).isFalse();
    }
}
