package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to retrieve configuration from Dapr configuration store.
 *
 * <p>Enforced by {@code DaprConfigurationAspect}: the value is converted to the method's return
 * type; without a value and without a {@link #defaultValue()} the method body runs.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprConfiguration(storeName = "configstore", key = "database.connection-string")
 * public String getDatabaseConnectionString() {
 *     return null; // Will be replaced with config value
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprConfiguration {

    /**
     * Configuration store name; empty for the service default ({@code adhar.dapr.config-store}).
     */
    String storeName() default "";

    /**
     * Configuration key.
     */
    String key();

    /**
     * Default value if configuration not found.
     */
    String defaultValue() default "";

    /**
     * Keep the value live: subscribe on first use and answer later calls from the subscription.
     */
    boolean subscribe() default false;
}

