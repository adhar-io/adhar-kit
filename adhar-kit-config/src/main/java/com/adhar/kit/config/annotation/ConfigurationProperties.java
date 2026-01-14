package com.adhar.kit.config.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as externalized configuration.
 *
 * <p>Enables binding of external configuration to Java objects.</p>
 *
 * <p><b>Example - Database Configuration:</b></p>
 * <pre>{@code
 * @ConfigurationProperties(prefix = "database")
 * @Data
 * public class DatabaseConfig {
 *     private String url;
 *     private String username;
 *     private String password;
 *     private Pool pool = new Pool();
 *
 *     @Data
 *     public static class Pool {
 *         private int maxSize = 10;
 *         private int minIdle = 5;
 *         private long connectionTimeout = 30000;
 *     }
 * }
 *
 * // application.yml:
 * // database:
 * //   url: jdbc:postgresql://localhost:5432/mydb
 * //   username: dbuser
 * //   password: dbpass
 * //   pool:
 * //     max-size: 20
 * //     min-idle: 10
 * }</pre>
 *
 * <p><b>Example - Application Configuration:</b></p>
 * <pre>{@code
 * @ConfigurationProperties(prefix = "app")
 * @Data
 * public class AppConfig {
 *     private String name;
 *     private String version;
 *     private Server server = new Server();
 *     private Security security = new Security();
 *
 *     @Data
 *     public static class Server {
 *         private int port = 8080;
 *         private String contextPath = "/";
 *     }
 *
 *     @Data
 *     public static class Security {
 *         private boolean enabled = true;
 *         private List<String> allowedOrigins = new ArrayList<>();
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
public @interface ConfigurationProperties {

    /**
     * The property prefix to bind to.
     * <p>
     * Example: "database" will bind to database.* properties
     */
    String prefix() default "";

    /**
     * Whether to ignore unknown fields.
     */
    boolean ignoreUnknownFields() default true;

    /**
     * Whether to ignore invalid fields.
     */
    boolean ignoreInvalidFields() default false;
}

