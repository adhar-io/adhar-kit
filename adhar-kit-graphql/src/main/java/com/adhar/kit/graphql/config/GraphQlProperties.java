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
     * Automatic Persisted Queries (APQ) configuration.
     */
    private PersistedQueries persistedQueries = new PersistedQueries();

    /**
     * Query allow-listing configuration.
     */
    private AllowList allowList = new AllowList();

    /**
     * Per-client query-cost rate limiting configuration.
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * Resolver tracing (Micrometer timers and Apollo-style tracing extension) configuration.
     */
    private Tracing tracing = new Tracing();

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

        /**
         * Enable field-level authorization via the {@code @auth(roles: [...])} schema
         * directive and the {@code @FieldAuthorization} annotation. Only takes effect
         * when Spring Security is on the classpath.
         */
        private boolean fieldAuthorizationEnabled = true;
    }

    /**
     * Settings for query allow-listing (approved/persisted operations only).
     */
    @Data
    public static class AllowList {

        /**
         * Enable allow-list enforcement. When enabled, requests whose query is not
         * registered in the {@code AllowedQueryRegistry} (by SHA-256 hash or operation
         * name) are rejected before execution.
         */
        private boolean enabled = false;

        /**
         * Classpath directory to load approved query documents from. Every
         * {@code *.graphql}/{@code *.gql} file below this directory is registered.
         */
        private String location = "graphql/allowed-queries";
    }

    /**
     * Settings for per-client query-cost rate limiting (token bucket).
     */
    @Data
    public static class RateLimit {

        /**
         * Enable per-client cost-based rate limiting.
         */
        private boolean enabled = false;

        /**
         * Token bucket capacity: the maximum query-cost budget a client can burst.
         */
        private long capacity = 1000;

        /**
         * Number of cost tokens refilled per second for each client bucket.
         */
        private double refillPerSecond = 100.0;

        /**
         * HTTP header used to identify the client. Falls back to the authenticated
         * principal name and then the remote address when absent.
         */
        private String clientIdHeader = "X-Client-Id";

        /**
         * Maximum number of distinct client buckets to retain in memory.
         */
        private int maxClients = 10_000;
    }

    /**
     * Settings for resolver (DataFetcher) tracing.
     */
    @Data
    public static class Tracing {

        /**
         * Enable resolver tracing: each non-trivial DataFetcher invocation is timed
         * and emitted as a Micrometer timer.
         */
        private boolean enabled = false;

        /**
         * Debug flag: when true, an Apollo-tracing-style {@code tracing} extension with
         * per-resolver timings is added to every GraphQL response.
         */
        private boolean apolloTracingEnabled = false;

        /**
         * Also time trivial (property-access) DataFetchers. Disabled by default to
         * keep metric cardinality and overhead low.
         */
        private boolean includeTrivialDataFetchers = false;
    }

    /**
     * Settings for Automatic Persisted Queries (Apollo APQ protocol).
     */
    @Data
    public static class PersistedQueries {

        /**
         * Enable Automatic Persisted Query support.
         */
        private boolean enabled = false;

        /**
         * Maximum number of persisted queries to retain in the bounded in-memory cache.
         */
        private int maxCacheSize = 1000;
    }
}
