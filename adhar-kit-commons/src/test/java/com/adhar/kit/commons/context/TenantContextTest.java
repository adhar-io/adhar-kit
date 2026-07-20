package com.adhar.kit.commons.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_shouldRoundTrip() {
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.isSet()).isFalse();
        TenantContext.setTenantId("tenant-1");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-1");
        assertThat(TenantContext.isSet()).isTrue();
    }

    @Test
    void clear_shouldRemoveTenant() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void runWith_shouldBindAndClear() {
        TenantContext.runWith("tenant-2", () ->
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-2"));
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void runWith_shouldRestorePreviousTenant() {
        TenantContext.setTenantId("outer");
        TenantContext.runWith("inner", () ->
            assertThat(TenantContext.getTenantId()).isEqualTo("inner"));
        assertThat(TenantContext.getTenantId()).isEqualTo("outer");
    }

    @Test
    void runWith_shouldRestoreEvenWhenActionThrows() {
        assertThatThrownBy(() -> TenantContext.runWith("t", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void callWith_shouldReturnResultAndRestore() {
        TenantContext.setTenantId("outer");
        String result = TenantContext.callWith("inner", TenantContext::getTenantId);
        assertThat(result).isEqualTo("inner");
        assertThat(TenantContext.getTenantId()).isEqualTo("outer");
    }

    @Test
    void isolation_shouldNotLeakAcrossThreads() throws Exception {
        TenantContext.setTenantId("main-thread");
        String[] seen = new String[1];
        Thread other = new Thread(() -> seen[0] = TenantContext.getTenantId());
        other.start();
        other.join();
        assertThat(seen[0]).isNull();
    }

    @Test
    void constructor_shouldBeUnsupported() throws Exception {
        Constructor<TenantContext> constructor = TenantContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
