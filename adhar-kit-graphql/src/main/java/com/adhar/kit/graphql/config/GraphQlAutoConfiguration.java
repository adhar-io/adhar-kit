package com.adhar.kit.graphql.config;

import com.adhar.kit.graphql.exception.GraphQlExceptionResolver;
import com.adhar.kit.graphql.instrumentation.QueryComplexityInstrumentation;
import com.adhar.kit.graphql.scalar.DateTimeScalar;
import graphql.schema.GraphQLScalarType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Auto-configuration for Adhar GraphQL module.
 *
 * <p>Automatically configures GraphQL support with custom scalars, query complexity
 * limits, and centralized exception handling.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Custom {@code DateTime} scalar type backed by {@link java.time.LocalDateTime}</li>
 *   <li>Query complexity and depth instrumentation to prevent abuse</li>
 *   <li>Centralized exception resolution for consistent error responses</li>
 *   <li>Configurable introspection, CORS, and security settings</li>
 * </ul>
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
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(GraphQlProperties.class)
@ConditionalOnClass(graphql.GraphQL.class)
@ConditionalOnProperty(prefix = "adhar.graphql", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GraphQlAutoConfiguration {

    private final GraphQlProperties properties;

    public GraphQlAutoConfiguration(GraphQlProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void logGraphQlConfiguration() {
        log.info("Adhar GraphQL module initialized - introspection: {}, maxDepth: {}, maxComplexity: {}",
                properties.isIntrospectionEnabled(),
                properties.getMaxQueryDepth(),
                properties.getMaxQueryComplexity());
    }

    /**
     * Registers the custom {@code DateTime} scalar type for use in GraphQL schemas.
     *
     * @return the DateTime {@link GraphQLScalarType}
     */
    @Bean
    @ConditionalOnMissingBean(name = "dateTimeScalarType")
    public GraphQLScalarType dateTimeScalarType() {
        log.info("Registering custom DateTime scalar type");
        return DateTimeScalar.DATE_TIME;
    }

    /**
     * Configures the GraphQL runtime wiring with custom scalar types.
     *
     * @param dateTimeScalarType the DateTime scalar
     * @return a {@link RuntimeWiringConfigurer} that registers custom scalars
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.graphql.execution.RuntimeWiringConfigurer")
    public RuntimeWiringConfigurer adharGraphQlRuntimeWiringConfigurer(GraphQLScalarType dateTimeScalarType) {
        log.info("Configuring GraphQL runtime wiring with custom scalars");
        return wiringBuilder -> wiringBuilder.scalar(dateTimeScalarType);
    }

    /**
     * Creates the query complexity and depth instrumentation.
     *
     * @return instrumentation that enforces complexity and depth limits
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryComplexityInstrumentation queryComplexityInstrumentation() {
        log.info("Configuring query complexity instrumentation - maxComplexity: {}, maxDepth: {}",
                properties.getMaxQueryComplexity(), properties.getMaxQueryDepth());
        return new QueryComplexityInstrumentation(
                properties.getMaxQueryComplexity(),
                properties.getMaxQueryDepth());
    }

    /**
     * Creates the centralized GraphQL exception resolver.
     *
     * @return a {@link DataFetcherExceptionResolver} for consistent error handling
     */
    @Bean
    @ConditionalOnMissingBean
    public GraphQlExceptionResolver graphQlExceptionResolver() {
        log.info("Registering GraphQL exception resolver");
        return new GraphQlExceptionResolver();
    }
}
