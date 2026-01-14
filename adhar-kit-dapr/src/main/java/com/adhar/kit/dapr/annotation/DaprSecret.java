package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a field to be injected with a secret from Dapr secret store.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class DatabaseService {
 *
 *     @DaprSecret(storeName = "secretstore", secretName = "database-password")
 *     private String databasePassword;
 *
 *     @DaprSecret(storeName = "secretstore", secretName = "api-key", key = "key")
 *     private String apiKey;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprSecret {

    /**
     * Secret store name.
     */
    String storeName() default "secretstore";

    /**
     * Secret name.
     */
    String secretName();

    /**
     * Secret key (for multi-value secrets).
     */
    String key() default "";
}

