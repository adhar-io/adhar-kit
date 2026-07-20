package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wraps the annotated method as a tracked batch job run: a BATCH STARTED event is published when
 * the method begins and a summary event (SUCCESS or FAILURE, with duration) when it finishes.
 *
 * <p>For item-level progress/error tracking use
 * {@link com.adhar.adharkit.logging.batch.BatchJobLogger} programmatically instead.</p>
 *
 * <pre>{@code
 * @LogBatchJob("nightly-reconciliation")
 * @Scheduled(cron = "0 0 2 * * *")
 * public void reconcile() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogBatchJob {

    /** Job name; defaults to {@code ClassName.methodName}. */
    String value() default "";
}
