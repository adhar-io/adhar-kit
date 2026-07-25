package com.adhar.kit.tracing.async;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TraceContextTaskDecorator}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceContextTaskDecoratorTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    private TraceContextTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new TraceContextTaskDecorator(tracer);
    }

    @Test
    void decorateWithActiveSpanReattachesContextForDecoratedRunnable() {
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
        AtomicBoolean ran = new AtomicBoolean(false);

        Runnable decorated = decorator.decorate(() -> ran.set(true));
        // Simulate handing the task off to a different thread: decoration already captured
        // the span above, so invoking it here (or from another thread) must re-attach it.
        decorated.run();

        assertThat(ran).isTrue();
        verify(tracer).withSpan(span);
        verify(spanInScope).close();
    }

    @Test
    void decorateWithoutActiveSpanReturnsOriginalRunnable() {
        when(tracer.currentSpan()).thenReturn(null);
        Runnable original = () -> {};

        Runnable decorated = decorator.decorate(original);

        assertThat(decorated).isSameAs(original);
    }

    @Test
    void decorateClosesScopeEvenWhenRunnableThrows() {
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
        RuntimeException boom = new RuntimeException("boom");

        Runnable decorated = decorator.decorate(() -> { throw boom; });

        try {
            decorated.run();
        } catch (RuntimeException caught) {
            assertThat(caught).isSameAs(boom);
        }

        verify(tracer).withSpan(span);
        verify(spanInScope).close();
    }

    @Test
    void decorateRunsOnDifferentThreadStillSeesReattachedSpan() throws InterruptedException {
        when(tracer.currentSpan()).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(spanInScope);
        AtomicBoolean ran = new AtomicBoolean(false);

        Runnable decorated = decorator.decorate(() -> ran.set(true));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(ran).isTrue();
        verify(tracer).withSpan(span);
    }
}
