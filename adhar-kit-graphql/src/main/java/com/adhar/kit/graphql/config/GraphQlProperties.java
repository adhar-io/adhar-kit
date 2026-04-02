package com.adhar.kit.graphql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Adhar GraphQL module.
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
}
