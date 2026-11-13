package com.adhar.kit.batch.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One parameter of an {@link AdharBatchJob}.
 *
 * <p>Identifying parameters make the run's identity: two runs with the same identifying values
 * are the same JobInstance, so the second is a duplicate or a restart, never a second execution
 * of the work. Names that look like credentials are refused (parameters are stored in clear text);
 * credentials come from Secrets.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobParam {

    /**
     * @return the parameter name
     */
    String name();

    /**
     * @return the value's type
     */
    ParamType type() default ParamType.STRING;

    /**
     * @return whether the value is part of the run's identity
     */
    boolean identifying() default true;

    /**
     * @return whether a run without a value (and without a default) is refused
     */
    boolean required() default false;

    /**
     * @return the value when a run gives none; empty means no default
     */
    String defaultValue() default "";

    /**
     * @return a regular expression the whole value must match; empty means any
     */
    String pattern() default "";

    /**
     * @return the only values accepted; empty means any
     */
    String[] allowed() default {};

    /**
     * @return where the value comes from when a run gives none
     */
    ParamSource source() default ParamSource.INPUT;

    /**
     * @return whether the value is masked in logs, the termination summary and events
     */
    boolean sensitive() default false;

    /**
     * @return what the parameter means, for the run form
     */
    String description() default "";
}
