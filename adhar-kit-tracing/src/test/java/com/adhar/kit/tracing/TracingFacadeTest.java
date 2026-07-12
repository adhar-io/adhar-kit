package com.adhar.kit.tracing;

import com.adhar.kit.tracing.api.TracingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TracingFacade}.
 *
 * <p>Because the package-private/private constructor selects a delegate based on the
 * detected framework (Spring Boot on the test classpath, whose adapter is meant to be
 * injected rather than built here), the facade is instantiated via Objenesis with a
 * mocked delegate so the pass-through methods can be verified in isolation. The
 * framework-specific factory methods are exercised directly via reflection.</p>
 */
@ExtendWith(MockitoExtension.class)
class TracingFacadeTest {

    @Mock
    private TracingService delegate;

    private TracingFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        facade = new ObjenesisStd().newInstance(TracingFacade.class);
        Field delegateField = TracingFacade.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        delegateField.set(facade, delegate);
    }

    @Test
    void spanBuilderDelegates() {
        TracingService.SpanBuilder sb = org.mockito.Mockito.mock(TracingService.SpanBuilder.class);
        when(delegate.spanBuilder("s")).thenReturn(sb);

        assertThat(facade.spanBuilder("s")).isSameAs(sb);
        verify(delegate).spanBuilder("s");
    }

    @Test
    void executeInSpanSupplierDelegates() {
        Supplier<String> op = () -> "x";
        when(delegate.executeInSpan("s", op)).thenReturn("x");

        assertThat(facade.executeInSpan("s", op)).isEqualTo("x");
        verify(delegate).executeInSpan("s", op);
    }

    @Test
    void executeInSpanRunnableDelegates() {
        Runnable op = () -> {};
        facade.executeInSpan("s", op);
        verify(delegate).executeInSpan("s", op);
    }

    @Test
    void getCurrentTraceIdDelegates() {
        when(delegate.getCurrentTraceId()).thenReturn("trace");
        assertThat(facade.getCurrentTraceId()).isEqualTo("trace");
        verify(delegate).getCurrentTraceId();
    }

    @Test
    void getCurrentSpanIdDelegates() {
        when(delegate.getCurrentSpanId()).thenReturn("span");
        assertThat(facade.getCurrentSpanId()).isEqualTo("span");
        verify(delegate).getCurrentSpanId();
    }

    @Test
    void addTagDelegates() {
        facade.addTag("k", "v");
        verify(delegate).addTag("k", "v");
    }

    @Test
    void addEventDelegates() {
        facade.addEvent("e");
        verify(delegate).addEvent("e");
    }

    @Test
    void getInstanceFailsForSpringBecauseAdapterMustBeInjected() {
        // On the Spring Boot test classpath the facade refuses to build a Spring adapter.
        assertThatThrownBy(TracingFacade::getInstance)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Spring adapter");
    }

    @Test
    void createSpringAdapterThrowsUnsupported() {
        assertThatThrownBy(() -> invoke("createSpringAdapter"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Spring adapter");
    }

    @Test
    void createQuarkusAdapterThrowsUnsupported() {
        assertThatThrownBy(() -> invoke("createQuarkusAdapter"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Quarkus");
    }

    @Test
    void createMicronautAdapterThrowsUnsupported() {
        assertThatThrownBy(() -> invoke("createMicronautAdapter"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Micronaut");
    }

    @Test
    void createHelidonAdapterThrowsWhenNotOnClasspath() {
        assertThatThrownBy(() -> invoke("createHelidonAdapter"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Helidon");
    }

    @Test
    void createVertxAdapterThrowsWhenNotOnClasspath() {
        assertThatThrownBy(() -> invoke("createVertxAdapter"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vert.x");
    }

    /** Invoke a private no-arg factory method, unwrapping the reflection wrapper. */
    private void invoke(String methodName) throws Throwable {
        Method m = TracingFacade.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        try {
            m.invoke(facade);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
