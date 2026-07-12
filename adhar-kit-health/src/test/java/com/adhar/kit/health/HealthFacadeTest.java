package com.adhar.kit.health;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.api.HealthService.HealthStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HealthFacade}.
 *
 * <p>The facade resolves a framework-specific delegate in its private constructor, and
 * for every framework the delegate creation throws (Spring/Quarkus/Micronaut adapters are
 * unsupported through the facade, and Helidon/Vert.x adapters are not on the classpath).
 * These tests drive the framework dispatch via {@link FrameworkDetector} and exercise the
 * delegating methods on an instance whose delegate is injected reflectively.</p>
 */
class HealthFacadeTest {

    @AfterEach
    void reset() throws Exception {
        resetDetection();
        resetInstance();
    }

    @Test
    void getInstance_onSpringBootClasspath_throwsUnsupported() throws Exception {
        resetDetection();
        resetInstance();
        // Spring Boot is detected on the test classpath.
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Spring adapter");
    }

    @Test
    void createDelegate_quarkus_throwsUnsupported() throws Exception {
        force(Framework.QUARKUS);
        resetInstance();
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Quarkus");
    }

    @Test
    void createDelegate_micronaut_throwsUnsupported() throws Exception {
        force(Framework.MICRONAUT);
        resetInstance();
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Micronaut");
    }

    @Test
    void createDelegate_helidon_throwsIllegalStateBecauseAdapterMissing() throws Exception {
        force(Framework.HELIDON);
        resetInstance();
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Helidon");
    }

    @Test
    void createDelegate_vertx_throwsIllegalStateBecauseAdapterMissing() throws Exception {
        force(Framework.VERTX);
        resetInstance();
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Vert.x");
    }

    @Test
    void createDelegate_unknownFramework_throwsIllegalState() throws Exception {
        force(Framework.OTHER);
        resetInstance();
        assertThatThrownBy(HealthFacade::getInstance)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported framework");
    }

    @Test
    void delegatingMethods_forwardToDelegate() throws Exception {
        HealthService delegate = mock(HealthService.class);
        HealthFacade facade = facadeWithDelegate(delegate);

        Supplier<HealthStatus> check = () -> HealthStatus.UP;
        facade.registerHealthCheck("h", check);
        facade.registerLivenessCheck("l", check);
        facade.registerReadinessCheck("r", check);
        verify(delegate).registerHealthCheck("h", check);
        verify(delegate).registerLivenessCheck("l", check);
        verify(delegate).registerReadinessCheck("r", check);

        when(delegate.getHealth()).thenReturn(HealthStatus.UP);
        when(delegate.getLiveness()).thenReturn(HealthStatus.DOWN);
        when(delegate.getReadiness()).thenReturn(HealthStatus.UNKNOWN);
        when(delegate.getDetailedHealth()).thenReturn(Map.of("h", HealthStatus.UP));
        when(delegate.unregisterHealthCheck("h")).thenReturn(true);
        when(delegate.hasHealthCheck("h")).thenReturn(true);

        assertThat(facade.getHealth()).isEqualTo(HealthStatus.UP);
        assertThat(facade.getLiveness()).isEqualTo(HealthStatus.DOWN);
        assertThat(facade.getReadiness()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(facade.getDetailedHealth()).containsEntry("h", HealthStatus.UP);
        assertThat(facade.unregisterHealthCheck("h")).isTrue();
        assertThat(facade.hasHealthCheck("h")).isTrue();
    }

    // ---- reflection helpers ----

    private static void force(Framework framework) throws Exception {
        Method m = FrameworkDetector.class.getDeclaredMethod("forceFramework", Framework.class);
        m.setAccessible(true);
        m.invoke(null, framework);
    }

    private static void resetDetection() throws Exception {
        Method m = FrameworkDetector.class.getDeclaredMethod("resetDetection");
        m.setAccessible(true);
        m.invoke(null);
    }

    private static void resetInstance() throws Exception {
        Field f = HealthFacade.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    /** Allocates a HealthFacade without running its (always-throwing) constructor. */
    private static HealthFacade facadeWithDelegate(HealthService delegate) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        HealthFacade facade = (HealthFacade) allocateInstance.invoke(unsafe, HealthFacade.class);

        Field delegateField = HealthFacade.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        delegateField.set(facade, delegate);
        return facade;
    }
}
