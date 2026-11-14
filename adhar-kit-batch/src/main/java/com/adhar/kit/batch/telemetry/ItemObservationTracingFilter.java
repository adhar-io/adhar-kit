package com.adhar.kit.batch.telemetry;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Keeps Spring Batch's per-ITEM observations ({@code spring.batch.item.read},
 * {@code spring.batch.item.process}) out of the traces while their metrics stay: a million-item run
 * would otherwise export two million spans. Job, step and chunk observations are traced as usual.
 *
 * <p>Every {@link TracingObservationHandler} bean is wrapped in one that opens no span for an
 * item observation. The meter handlers are untouched, so {@code spring_batch_item_read_*} (which the
 * {@code batch-jvm} dashboard reads) is still recorded. Off with
 * {@code adhar.batch.telemetry.trace-items=true}.</p>
 *
 * @since 0.1.0
 */
public final class ItemObservationTracingFilter implements BeanPostProcessor {

    /** The prefix of Spring Batch's per-item observations. */
    public static final String ITEM_OBSERVATION_PREFIX = "spring.batch.item.";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof TracingObservationHandler<?> handler && !(bean instanceof ItemsExcluded<?>)) {
            return wrap(handler);
        }
        return bean;
    }

    /**
     * @param handler a tracing handler
     * @param <T>     its context type
     * @return the same handler, declining per-item observations
     */
    public static <T extends Observation.Context> TracingObservationHandler<T> wrap(TracingObservationHandler<T> handler) {
        return new ItemsExcluded<>(handler);
    }

    /**
     * @param context an observation
     * @return whether it is one of Spring Batch's per-item observations
     */
    public static boolean isItemObservation(Observation.Context context) {
        String name = context.getName();
        return name != null && name.startsWith(ITEM_OBSERVATION_PREFIX);
    }

    /**
     * A tracing handler that opens no span for a per-item observation and delegates everything else.
     *
     * <p>It must still SUPPORT the item observation and give it an empty {@link TracingContext}:
     * Spring Boot groups the meter handlers behind {@code TracingAwareMeterObservationHandler},
     * which requires that context when the observation stops. Merely declining the observation
     * made every chunk fail with "Context does not have an entry for key TracingContext".</p>
     */
    private static final class ItemsExcluded<T extends Observation.Context> implements TracingObservationHandler<T> {

        private final TracingObservationHandler<T> delegate;

        private ItemsExcluded(TracingObservationHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return delegate.supportsContext(context);
        }

        @Override
        public Tracer getTracer() {
            return delegate.getTracer();
        }

        @Override
        public void onStart(T context) {
            if (isItemObservation(context)) {
                getTracingContext(context);
                return;
            }
            delegate.onStart(context);
        }

        @Override
        public void onStop(T context) {
            if (!isItemObservation(context)) {
                delegate.onStop(context);
            }
        }

        @Override
        public void onError(T context) {
            if (!isItemObservation(context)) {
                delegate.onError(context);
            }
        }

        @Override
        public void onEvent(Observation.Event event, T context) {
            if (!isItemObservation(context)) {
                delegate.onEvent(event, context);
            }
        }

        @Override
        public void onScopeOpened(T context) {
            if (!isItemObservation(context)) {
                delegate.onScopeOpened(context);
            }
        }

        @Override
        public void onScopeClosed(T context) {
            if (!isItemObservation(context)) {
                delegate.onScopeClosed(context);
            }
        }

        @Override
        public void onScopeReset(T context) {
            if (!isItemObservation(context)) {
                delegate.onScopeReset(context);
            }
        }

        @Override
        public void tagSpan(T context, Span span) {
            delegate.tagSpan(context, span);
        }

        @Override
        public String getSpanName(T context) {
            return delegate.getSpanName(context);
        }

        @Override
        public void endSpan(T context, Span span) {
            delegate.endSpan(context, span);
        }
    }
}
