package com.adhar.kit.kubernetes.annotation;

import java.lang.annotation.*;

/**
 * Marks a class for Kubernetes ConfigMap injection.
 *
 * <p>Automatically loads configuration from Kubernetes ConfigMap and injects into the annotated class.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @KubernetesConfigMap(name = "app-config", namespace = "default")
 * @Component
 * public class ApplicationConfig {
 *
 *     @Value("${database.url}")
 *     private String databaseUrl;
 *
 *     @Value("${api.key}")
 *     private String apiKey;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KubernetesConfigMap {

    /**
     * ConfigMap name.
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
    boolean reloadOnChange() default true;
}

