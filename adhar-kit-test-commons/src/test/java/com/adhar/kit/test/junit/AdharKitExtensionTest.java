package com.adhar.kit.test.junit;

import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterContext;

import java.lang.reflect.Parameter;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AdharKitExtension}. The container-startup path needs Docker and is not exercised;
 * everything else - annotation reading, injectable-value resolution, field injection and parameter
 * resolution - is unit-tested with mocks and sample classes.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("AdharKitExtension Tests")
class AdharKitExtensionTest {

    private final AdharKitExtension extension = new AdharKitExtension();

    @BeforeEach
    @AfterEach
    void reset() {
        ContainerConnectionInfo.getInstance().clear();
    }

    // ---- annotation reading ---------------------------------------------------------------

    @AdharIntegrationTest({AdharContainer.POSTGRES, AdharContainer.REDIS, AdharContainer.POSTGRES})
    static class Annotated {
    }

    static class NotAnnotated {
    }

    @Test
    @DisplayName("declaredContainers should read declared containers and de-duplicate them")
    void testDeclaredContainers() {
        Set<AdharContainer> containers = AdharKitExtension.declaredContainers(Annotated.class);
        assertEquals(2, containers.size());
        assertTrue(containers.contains(AdharContainer.POSTGRES));
        assertTrue(containers.contains(AdharContainer.REDIS));
    }

    @Test
    @DisplayName("declaredContainers should be empty when the annotation is absent")
    void testDeclaredContainersAbsent() {
        assertTrue(AdharKitExtension.declaredContainers(NotAnnotated.class).isEmpty());
    }

    // ---- injectable-value resolution ------------------------------------------------------

    @Test
    @DisplayName("resolveInjection should return the connection-info and registry singletons")
    void testResolveSingletons() {
        assertSame(ContainerConnectionInfo.getInstance(),
                AdharKitExtension.resolveInjection(ContainerConnectionInfo.class, null));
        assertSame(TestContainerRegistry.getInstance(),
                AdharKitExtension.resolveInjection(TestContainerRegistry.class, null));
    }

    @Test
    @DisplayName("resolveInjection should return a property value for @ContainerProperty String")
    void testResolveProperty() {
        ContainerConnectionInfo.getInstance().put("k", "v");
        ContainerProperty property = property("k");

        assertEquals("v", AdharKitExtension.resolveInjection(String.class, property));
    }

    @Test
    @DisplayName("resolveInjection should reject @ContainerProperty on a non-String type")
    void testResolvePropertyWrongType() {
        assertThrows(IllegalStateException.class,
                () -> AdharKitExtension.resolveInjection(Integer.class, property("k")));
    }

    @Test
    @DisplayName("resolveInjection should return UNSUPPORTED for other types")
    void testResolveUnsupported() {
        assertSame(AdharKitExtension.UNSUPPORTED, AdharKitExtension.resolveInjection(String.class, null));
        assertSame(AdharKitExtension.UNSUPPORTED, AdharKitExtension.resolveInjection(Object.class, null));
    }

    // ---- field injection ------------------------------------------------------------------

    static class SampleTest {
        String plain;
        @ContainerProperty("spring.datasource.url")
        String jdbcUrl;
        ContainerConnectionInfo info;
        TestContainerRegistry registry;
    }

    @Test
    @DisplayName("postProcessTestInstance should inject matching fields and leave others untouched")
    void testFieldInjection() throws Exception {
        ContainerConnectionInfo.getInstance().put("spring.datasource.url", "jdbc:postgresql://h/db");
        SampleTest instance = new SampleTest();

        extension.postProcessTestInstance(instance, null);

        assertEquals("jdbc:postgresql://h/db", instance.jdbcUrl);
        assertSame(ContainerConnectionInfo.getInstance(), instance.info);
        assertSame(TestContainerRegistry.getInstance(), instance.registry);
        assertNull(instance.plain);
    }

    // ---- parameter resolution -------------------------------------------------------------

    void infoParam(ContainerConnectionInfo info) {
    }

    void propParam(@ContainerProperty("k") String value) {
    }

    void plainParam(String value) {
    }

    private Parameter parameterOf(String methodName) throws Exception {
        return getClass().getDeclaredMethod(methodName, methodName.equals("infoParam")
                ? ContainerConnectionInfo.class : String.class).getParameters()[0];
    }

    private ParameterContext parameterContext(String methodName) throws Exception {
        ParameterContext ctx = mock(ParameterContext.class);
        when(ctx.getParameter()).thenReturn(parameterOf(methodName));
        return ctx;
    }

    @Test
    @DisplayName("supportsParameter should be true for injectable types and annotated properties")
    void testSupportsParameter() throws Exception {
        assertTrue(extension.supportsParameter(parameterContext("infoParam"), null));
        assertTrue(extension.supportsParameter(parameterContext("propParam"), null));
        assertFalse(extension.supportsParameter(parameterContext("plainParam"), null));
    }

    @Test
    @DisplayName("resolveParameter should resolve injectable types and property values")
    void testResolveParameter() throws Exception {
        ContainerConnectionInfo.getInstance().put("k", "v");

        assertSame(ContainerConnectionInfo.getInstance(),
                extension.resolveParameter(parameterContext("infoParam"), null));
        assertEquals("v", extension.resolveParameter(parameterContext("propParam"), null));
        assertNull(extension.resolveParameter(parameterContext("plainParam"), null));
    }

    // ---- helpers --------------------------------------------------------------------------

    private static ContainerProperty property(String value) {
        return new ContainerProperty() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ContainerProperty.class;
            }

            @Override
            public String value() {
                return value;
            }
        };
    }
}
