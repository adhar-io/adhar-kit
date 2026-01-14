package com.adhar.kit.docs.annotation;

import java.lang.annotation.*;

/**
 * Enables automatic OpenAPI documentation generation.
 *
 * <p>Place on your application main class to enable auto-documentation.</p>
 *
 * <p><b>Spring Boot Example:</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableOpenApiDocs
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Quarkus Example:</b></p>
 * <pre>{@code
 * @ApplicationPath("/api")
 * @EnableOpenApiDocs
 * public class ApiApplication extends Application {
 * }
 * }</pre>
 *
 * <p><b>Micronaut Example:</b></p>
 * <pre>{@code
 * @EnableOpenApiDocs
 * public class Application {
 *     public static void main(String[] args) {
 *         Micronaut.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableOpenApiDocs {

    /**
     * API title.
     */
    String title() default "API Documentation";

    /**
     * API version.
     */
    String version() default "1.0.0";

    /**
     * API description.
     */
    String description() default "";

    /**
     * Enable JWT security.
     */
    boolean enableJwtSecurity() default false;

    /**
     * Enable API key security.
     */
    boolean enableApiKeySecurity() default false;

    /**
     * Packages to scan for API endpoints.
     */
    String[] packagesToScan() default {};
}

