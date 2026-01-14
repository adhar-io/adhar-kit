package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to retrieve configuration from Dapr configuration store.
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
     * Configuration store name.
     */
    String storeName() default "configstore";

    /**
     * Configuration key.
     */
    String key();

    /**
     * Default value if configuration not found.
     */
    String defaultValue() default "";

    /**
     * Subscribe to configuration changes.
     */
    boolean subscribe() default false;
}

