package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a field to be injected with a secret from Dapr secret store.
 *
 * <p>Injected by {@code DaprSecretInjector} when the bean is created; a secret that cannot be
 * read fails startup, like a missing required property. In memory mode seed secrets with
 * {@code adhar.dapr.memory.secrets.<name>.<key>=...}. To bind secrets to configuration properties
 * instead, import them: {@code spring.config.import=optional:dapr-secrets:secretstore/db}.</p>
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
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprSecret {

    /**
     * Secret store name; empty for the service default ({@code adhar.dapr.secret-store}).
     */
    String storeName() default "";

    /**
     * Secret name.
     */
    String secretName();

    /**
     * Secret key (for multi-value secrets); empty for the value named like the secret, or the only
     * value of a single-value secret. A {@code Map<String, String>} field receives every value.
     */
    String key() default "";
}

