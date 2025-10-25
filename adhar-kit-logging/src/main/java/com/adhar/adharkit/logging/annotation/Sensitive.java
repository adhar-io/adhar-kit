package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or parameter as sensitive for logging purposes.
 * When logging arguments or results, sensitive data will be masked.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Sensitive {

    /**
     * Custom mask pattern to use for this sensitive field.
     * Default is "***"
     */
    String mask() default "***";

    /**
     * Number of characters to show from the beginning before masking.
     * Default is 0 (complete masking)
     */
    int showFirst() default 0;

    /**
     * Number of characters to show from the end before masking.
     * Default is 0 (complete masking)
     */
    int showLast() default 0;
}
