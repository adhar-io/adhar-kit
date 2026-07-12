package com.adhar.kit.profiler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilingModelTest {

    @Test
    @DisplayName("ProfilingResult.isSlow is true only when duration exceeds threshold")
    void profilingResultIsSlow() {
        ProfilingResult result = new ProfilingResult(
                "m", "c", 100L, true, null, Instant.now());

        assertThat(result.isSlow(50L)).isTrue();
        assertThat(result.isSlow(100L)).isFalse();
        assertThat(result.isSlow(150L)).isFalse();
    }

    @Test
    @DisplayName("ProfilingResult exposes its components")
    void profilingResultAccessors() {
        Instant ts = Instant.now();
        ProfilingResult result = new ProfilingResult(
                "method", "Class", 25L, false, "BadThing", ts);

        assertThat(result.methodName()).isEqualTo("method");
        assertThat(result.className()).isEqualTo("Class");
        assertThat(result.durationMs()).isEqualTo(25L);
        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("BadThing");
        assertThat(result.timestamp()).isEqualTo(ts);
    }

    @Test
    @DisplayName("MethodProfile averageTimeMs and errorRate handle the zero-call case")
    void methodProfileZeroCalls() {
        MethodProfile empty = new MethodProfile(
                "n", 0L, 0L, 0L, 0L, 0L, Instant.now());

        assertThat(empty.averageTimeMs()).isZero();
        assertThat(empty.errorRate()).isZero();
    }

    @Test
    @DisplayName("MethodProfile computes average and error rate for populated stats")
    void methodProfileComputedValues() {
        MethodProfile profile = new MethodProfile(
                "n", 4L, 200L, 10L, 80L, 1L, Instant.now());

        assertThat(profile.averageTimeMs()).isEqualTo(50.0);
        assertThat(profile.errorRate()).isEqualTo(0.25);
    }
}
