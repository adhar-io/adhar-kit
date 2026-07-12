package com.adhar.kit.resilience;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link CircuitBreakerFacade}.
 *
 * <p>The framework-specific adapter classes (quarkus/micronaut/helidon/vertx) are excluded
 * from compilation in this module, so {@code createAdapterByReflection} can never resolve
 * them here. These tests therefore document the facade's framework-resolution behaviour:
 * Spring Boot demands injection, unknown frameworks fail fast, and every reflective adapter
 * branch surfaces a clear {@link IllegalStateException}.</p>
 */
@DisplayName("CircuitBreakerFacade Tests")
class CircuitBreakerFacadeTest {

    @AfterEach
    void tearDown() {
        FrameworkTestSupport.reset();
    }

    private static CircuitBreakerFacade newFacade() throws Exception {
        Constructor<CircuitBreakerFacade> ctor = CircuitBreakerFacade.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @Test
    @DisplayName("getInstance fails on Spring Boot: the adapter must be injected, not created")
    void getInstanceRejectsSpringBoot() {
        FrameworkTestSupport.reset(); // real classpath detection -> SPRING_BOOT
        assertThatThrownBy(CircuitBreakerFacade::getInstance)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SpringCircuitBreakerAdapter");
    }

    @Test
    @DisplayName("Spring Boot delegate creation is rejected via the private constructor too")
    void springBootConstructorRejected() {
        FrameworkTestSupport.force(Framework.SPRING_BOOT);
        assertThatThrownBy(CircuitBreakerFacadeTest::newFacade)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("unsupported framework (OTHER) fails fast when building the delegate")
    void unsupportedFrameworkFailsFast() {
        FrameworkTestSupport.force(Framework.OTHER);
        assertThatThrownBy(CircuitBreakerFacadeTest::newFacade)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported framework");
    }

    @ParameterizedTest
    @EnumSource(value = Framework.class, names = {"QUARKUS", "MICRONAUT", "HELIDON", "VERTX"})
    @DisplayName("each reflective adapter branch reports a missing adapter class")
    void reflectiveAdapterBranchesReportMissingClass(Framework framework) {
        FrameworkTestSupport.force(framework);
        assertThatThrownBy(CircuitBreakerFacadeTest::newFacade)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create adapter")
                .hasMessageContaining("on the classpath");
    }

    @Test
    @DisplayName("getInstance returns the same cached singleton instance")
    void getInstanceIsIdempotent() {
        // Spring Boot path always throws, so the singleton is never cached; verify the
        // detection wiring stays consistent across repeated calls.
        FrameworkTestSupport.reset();
        Throwable first = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, CircuitBreakerFacade::getInstance);
        Throwable second = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, CircuitBreakerFacade::getInstance);
        assertThat(first).isInstanceOf(second.getClass());
    }
}
