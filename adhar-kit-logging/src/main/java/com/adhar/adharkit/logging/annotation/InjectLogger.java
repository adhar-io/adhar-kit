package com.adhar.adharkit.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for automatic logger injection. Supported field types:
 * {@link org.slf4j.Logger} (named after the declaring class),
 * {@link com.adhar.adharkit.logging.LoggingFacade} and
 * {@link com.adhar.adharkit.logging.util.AdharLogger}.
 *
 * <p>Injection is performed by the
 * {@link com.adhar.adharkit.logging.processor.LoggerInjectionBeanPostProcessor} for every Spring
 * bean, removing logger boilerplate:</p>
 *
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @InjectLogger
 *     private Logger log;                // named "com.example.OrderService"
 *
 *     @InjectLogger
 *     private AdharLogger adharLogger;   // the shared AdharLogger bean
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectLogger {

    /** Optional explicit logger name (only for {@link org.slf4j.Logger} fields). */
    String value() default "";
}
