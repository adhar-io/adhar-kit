package com.adhar.kit.kubernetes.annotation;

import java.lang.annotation.*;

/**
 * Marks a class for Kubernetes Secret injection.
 *
 * <p>Automatically loads secrets from Kubernetes Secret and injects into the annotated class.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @KubernetesSecret(name = "app-secrets", namespace = "default")
 * @Component
 * public class ApplicationSecrets {
 *
 *     @Value("${database.password}")
 *     private String databasePassword;
 *
 *     @Value("${api.secret}")
 *     private String apiSecret;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KubernetesSecret {

    /**
     * Secret name.
     */
    String name();

    /**
     * Namespace (defaults to current namespace).
     */
    String namespace() default "";

    /**
     * Enable watching for changes.
     */
    boolean watch() default true;

    /**
     * Reload on change.
     */
    boolean reloadOnChange() default false; // Secrets usually don't auto-reload
}

