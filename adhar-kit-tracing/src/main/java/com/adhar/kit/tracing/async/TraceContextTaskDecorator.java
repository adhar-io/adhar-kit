package com.adhar.kit.tracing.async;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskDecorator;

/**
 * A Spring {@link TaskDecorator} that captures the current trace context (the current
 * {@link Span}, if any) at task-submission time, and re-attaches it for the duration of the
 * decorated {@link Runnable}'s execution on the executor's thread.
 * <p>
 * Wire this into any {@code ThreadPoolTaskExecutor} used to back {@code @Async} methods (or
 * any other {@link org.springframework.core.task.TaskExecutor}) so that work submitted from
 * within a span continues to see that span as "current" when it actually runs, even though it
 * runs on a different thread:
 * </p>
 *
 * <pre>{@code
 * @Bean
 * public Executor taskExecutor(TraceContextTaskDecorator taskDecorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(taskDecorator);
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public class TraceContextTaskDecorator implements TaskDecorator {

    private final Tracer tracer;

    @Override
    public Runnable decorate(Runnable runnable) {
        Span capturedSpan = tracer.currentSpan();
        if (capturedSpan == null) {
            return runnable;
        }

        return () -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                runnable.run();
            }
        };
    }
}
