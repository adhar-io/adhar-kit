package com.adhar.kit.health;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.api.HealthService.HealthStatus;
import com.adhar.kit.health.registry.RegistryHealthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HealthFacade}.
 *
 * <p>The facade now falls back to a registry-backed {@link RegistryHealthService}
 * delegate for every framework where a dedicated adapter is not reachable, so
 * {@code getInstance()} works everywhere. These tests drive the framework dispatch
 * via {@link FrameworkDetector} and verify both delegate selection and the
 * end-to-end check behavior of the fallback.</p>
 */
class HealthFacadeTest {

    @AfterEach
    void reset() throws Exception {
        resetDetection();
        resetInstance();
    }

    @Test
    void getInstance_onSpringBootClasspath_usesRegistryBackedDelegate() throws Exception {
        resetDetection(); // Spring Boot is detected on the test classpath
        resetInstance();

        HealthFacade facade = HealthFacade.getInstance();

        assertThat(delegateOf(facade)).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void getInstance_returnsSameSingleton() throws Exception {
        resetDetection();
        resetInstance();

        assertThat(HealthFacade.getInstance()).isSameAs(HealthFacade.getInstance());
    }

    @Test
    void createDelegate_quarkus_fallsBackToRegistry() throws Exception {
        force(Framework.QUARKUS);
        resetInstance();

        assertThat(delegateOf(HealthFacade.getInstance())).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void createDelegate_micronaut_fallsBackToRegistry() throws Exception {
        force(Framework.MICRONAUT);
        resetInstance();

        assertThat(delegateOf(HealthFacade.getInstance())).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void createDelegate_helidonAdapterMissing_fallsBackToRegistry() throws Exception {
        force(Framework.HELIDON);
        resetInstance();

        // Helidon adapter sources are excluded from compilation, so reflection fails
        // and the facade must fall back instead of throwing.
        assertThat(delegateOf(HealthFacade.getInstance())).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void createDelegate_vertxAdapterMissing_fallsBackToRegistry() throws Exception {
        force(Framework.VERTX);
        resetInstance();

        assertThat(delegateOf(HealthFacade.getInstance())).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void createDelegate_unknownFramework_usesRegistryBackedDelegate() throws Exception {
        force(Framework.OTHER);
        resetInstance();

        assertThat(delegateOf(HealthFacade.getInstance())).isInstanceOf(RegistryHealthService.class);
    }

    @Test
    void facade_isFunctionalEndToEnd_withFallbackDelegate() throws Exception {
        force(Framework.OTHER);
        resetInstance();
        HealthFacade facade = HealthFacade.getInstance();

        assertThat(facade.getHealth()).isEqualTo(HealthStatus.UP);

        facade.registerHealthCheck("ok", () -> HealthStatus.UP);
        facade.registerLivenessCheck("live", () -> HealthStatus.UP);
        facade.registerReadinessCheck("notReady", () -> HealthStatus.DOWN);

        assertThat(facade.getLiveness()).isEqualTo(HealthStatus.UP);
        assertThat(facade.getReadiness()).isEqualTo(HealthStatus.DOWN);
        assertThat(facade.getHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(facade.getDetailedHealth())
            .containsEntry("ok", HealthStatus.UP)
            .containsEntry("notReady", HealthStatus.DOWN);
        assertThat(facade.hasHealthCheck("ok")).isTrue();

        assertThat(facade.unregisterHealthCheck("notReady")).isTrue();
        assertThat(facade.getHealth()).isEqualTo(HealthStatus.UP);
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

    private static HealthService delegateOf(HealthFacade facade) throws Exception {
        Field delegateField = HealthFacade.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        return (HealthService) delegateField.get(facade);
    }

    /** Allocates a HealthFacade with an injected mock delegate. */
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
