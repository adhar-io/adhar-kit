package com.adhar.kit.commons.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the adhar-kit-commons auto-configuration.
 *
 * <p>All features default to enabled. Example:</p>
 * <pre>
 * adhar:
 *   commons:
 *     exception-handler:
 *       enabled: true
 *     idempotency:
 *       enabled: true
 *     correlation:
 *       enabled: true
 *     tenant:
 *       enabled: true
 *     api-versioning:
 *       enabled: true
 *       validate-request-version: false
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.commons")
public class AdharCommonsProperties {

    /** Global exception handler settings. */
    private final ExceptionHandler exceptionHandler = new ExceptionHandler();

    /** Idempotency runtime settings. */
    private final Idempotency idempotency = new Idempotency();

    /** Correlation-id filter settings. */
    private final Correlation correlation = new Correlation();

    /** Tenant-context filter settings. */
    private final Tenant tenant = new Tenant();

    /** API versioning interceptor settings. */
    private final ApiVersioning apiVersioning = new ApiVersioning();

    /** Toggle for the {@code SpringGlobalExceptionHandler} bean. */
    @Data
    public static class ExceptionHandler {
        /** Whether the global exception handler is registered. */
        private boolean enabled = true;
    }

    /** Toggle for the idempotency store and aspect beans. */
    @Data
    public static class Idempotency {
        /** Whether the idempotency runtime is registered. */
        private boolean enabled = true;
    }

    /** Toggle for the correlation-id servlet filter. */
    @Data
    public static class Correlation {
        /** Whether the correlation-id filter is registered. */
        private boolean enabled = true;
    }

    /** Toggle for the tenant-context servlet filter. */
    @Data
    public static class Tenant {
        /** Whether the tenant-context filter is registered. */
        private boolean enabled = true;
    }

    /** Settings for the API versioning interceptor. */
    @Data
    public static class ApiVersioning {
        /** Whether the {@code ApiVersionInterceptor} is registered. */
        private boolean enabled = true;
        /** Whether a mismatching {@code X-API-Version} request header is rejected with 400. */
        private boolean validateRequestVersion = false;
    }
}
