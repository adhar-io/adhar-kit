package com.adhar.kit.graphql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar GraphQL module.
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   graphql:
 *     enabled: true
 *     introspection-enabled: false
 *     max-query-depth: 10
 *     max-query-complexity: 200
 *     cors-enabled: true
 *     pagination:
 *       default-page-size: 20
 *       max-page-size: 100
 *     security:
 *       require-authentication: false
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.graphql")
public class GraphQlProperties {

    /**
     * Enable the Adhar GraphQL module.
     */
    private boolean enabled = true;

    /**
     * Enable GraphQL schema introspection.
     * Disabled by default for production security; enable in dev/staging profiles.
     */
    private boolean introspectionEnabled = false;

    /**
     * Maximum allowed query depth to prevent deeply nested query abuse.
     */
    private int maxQueryDepth = 10;

    /**
     * Maximum allowed query complexity score to prevent expensive queries.
     */
    private int maxQueryComplexity = 200;

    /**
     * Enable CORS support for GraphQL endpoints.
     */
    private boolean corsEnabled = true;

    /**
     * Pagination configuration for Relay-style cursor pagination.
     */
    private Pagination pagination = new Pagination();

    /**
     * Security configuration for GraphQL requests.
     */
    private Security security = new Security();

    /**
     * Pagination settings for cursor-based GraphQL pagination.
     */
    @Data
    public static class Pagination {

        /**
         * Default number of items per page when the client does not specify {@code first}.
         */
        private int defaultPageSize = 20;

        /**
         * Maximum allowed page size to prevent clients from requesting too many items at once.
         */
        private int maxPageSize = 100;
    }

    /**
     * Security settings for GraphQL request handling.
     */
    @Data
    public static class Security {

        /**
         * When true, all GraphQL requests require an authenticated {@link org.springframework.security.core.Authentication}
         * in the security context. Unauthenticated requests will be rejected.
         */
        private boolean requireAuthentication = false;
    }
}
