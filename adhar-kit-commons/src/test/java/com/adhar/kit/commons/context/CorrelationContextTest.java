package com.adhar.kit.commons.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationContextTest {

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
    }

    @Test
    void setAndGet_shouldRoundTripBothIds() {
        assertThat(CorrelationContext.getCorrelationId()).isNull();
        assertThat(CorrelationContext.getRequestId()).isNull();
        CorrelationContext.setCorrelationId("corr-1");
        CorrelationContext.setRequestId("req-1");
        assertThat(CorrelationContext.getCorrelationId()).isEqualTo("corr-1");
        assertThat(CorrelationContext.getRequestId()).isEqualTo("req-1");
    }

    @Test
    void clear_shouldRemoveBothIds() {
        CorrelationContext.setCorrelationId("corr-1");
        CorrelationContext.setRequestId("req-1");
        CorrelationContext.clear();
        assertThat(CorrelationContext.getCorrelationId()).isNull();
        assertThat(CorrelationContext.getRequestId()).isNull();
    }

    @Test
    void runWith_shouldBindAndClear() {
        CorrelationContext.runWith("corr-2", () ->
            assertThat(CorrelationContext.getCorrelationId()).isEqualTo("corr-2"));
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }

    @Test
    void runWith_shouldRestorePreviousCorrelationId() {
        CorrelationContext.setCorrelationId("outer");
        CorrelationContext.runWith("inner", () ->
            assertThat(CorrelationContext.getCorrelationId()).isEqualTo("inner"));
        assertThat(CorrelationContext.getCorrelationId()).isEqualTo("outer");
    }

    @Test
    void runWith_shouldRestoreEvenWhenActionThrows() {
        assertThatThrownBy(() -> CorrelationContext.runWith("c", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }

    @Test
    void callWith_shouldReturnResultAndRestore() {
        CorrelationContext.setCorrelationId("outer");
        String result = CorrelationContext.callWith("inner", CorrelationContext::getCorrelationId);
        assertThat(result).isEqualTo("inner");
        assertThat(CorrelationContext.getCorrelationId()).isEqualTo("outer");
    }

    @Test
    void isolation_shouldNotLeakAcrossThreads() throws Exception {
        CorrelationContext.setCorrelationId("main-thread");
        String[] seen = new String[1];
        Thread other = new Thread(() -> seen[0] = CorrelationContext.getCorrelationId());
        other.start();
        other.join();
        assertThat(seen[0]).isNull();
    }

    @Test
    void constructor_shouldBeUnsupported() throws Exception {
        Constructor<CorrelationContext> constructor = CorrelationContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
