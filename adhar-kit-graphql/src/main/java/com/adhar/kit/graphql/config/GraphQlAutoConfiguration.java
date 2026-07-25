package com.adhar.kit.graphql.config;

import com.adhar.kit.graphql.dataloader.CompositeBatchLoaderRegistry;
import com.adhar.kit.graphql.dataloader.DataLoaderRegistrar;
import com.adhar.kit.graphql.exception.GraphQlExceptionResolver;
import com.adhar.kit.graphql.instrumentation.QueryComplexityInstrumentation;
import com.adhar.kit.graphql.persisted.InMemoryPersistedQueryCache;
import com.adhar.kit.graphql.persisted.PersistedQueryCache;
import com.adhar.kit.graphql.persisted.PersistedQueryInterceptor;
import com.adhar.kit.graphql.scalar.DateTimeScalar;
import com.adhar.kit.graphql.schema.GraphQlSchemaRegistry;
import com.adhar.kit.graphql.security.GraphQlSecurityInterceptor;
import com.adhar.kit.graphql.validation.InputValidator;
import graphql.schema.GraphQLScalarType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Auto-configuration for the Adhar GraphQL module.
 *
 * <p>Automatically configures GraphQL support with custom scalars, query complexity
 * limits, centralized exception handling, schema registry, pagination, input
 * validation, DataLoader support, and security interceptor.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Custom {@code DateTime} scalar type backed by {@link java.time.LocalDateTime}</li>
 *   <li>Query complexity and depth instrumentation to prevent abuse</li>
 *   <li>Centralized exception resolution for consistent error responses</li>
 *   <li>Schema registry for dynamic SDL type, query, and mutation registration</li>
 *   <li>Input validation using Jakarta Validation annotations</li>
 *   <li>DataLoader registrar for N+1 query prevention</li>
 *   <li>Security interceptor with authentication enforcement and introspection control</li>
 *   <li>Configurable introspection, CORS, pagination, and security settings</li>
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
        log.info("Adhar GraphQL module initialized - introspection: {}, maxDepth: {}, maxComplexity: {}, " +
                        "defaultPageSize: {}, maxPageSize: {}, requireAuth: {}",
                properties.isIntrospectionEnabled(),
                properties.getMaxQueryDepth(),
                properties.getMaxQueryComplexity(),
                properties.getPagination().getDefaultPageSize(),
                properties.getPagination().getMaxPageSize(),
                properties.getSecurity().isRequireAuthentication());
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

    /**
     * Creates the GraphQL schema registry for dynamic SDL management.
     *
     * @return a thread-safe schema registry
     */
    @Bean
    @ConditionalOnMissingBean
    public GraphQlSchemaRegistry graphQlSchemaRegistry() {
        log.info("Registering GraphQL schema registry");
        return new GraphQlSchemaRegistry();
    }

    /**
     * Creates the DataLoader registrar for batch loading support.
     *
     * @return a DataLoader registrar for preventing N+1 queries
     */
    @Bean
    @ConditionalOnMissingBean
    public DataLoaderRegistrar dataLoaderRegistrar() {
        log.info("Registering DataLoader registrar");
        return new DataLoaderRegistrar();
    }

    /**
     * Creates the {@link BatchLoaderRegistry} bean consumed by Spring Boot's GraphQL
     * auto-configuration when wiring the {@code ExecutionGraphQlService}.
     *
     * <p>Composes the standard {@code @BatchMapping}/fluent registration mechanism with
     * the Adhar {@link DataLoaderRegistrar}'s simple named-loader registrations, so both
     * populate the same per-request {@code DataLoaderRegistry}.</p>
     *
     * @param dataLoaderRegistrar the Adhar DataLoader registrar to merge in
     * @return the composite batch loader registry
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.graphql.execution.BatchLoaderRegistry")
    public BatchLoaderRegistry batchLoaderRegistry(DataLoaderRegistrar dataLoaderRegistrar) {
        log.info("Registering composite BatchLoaderRegistry backed by the Adhar DataLoaderRegistrar");
        return new CompositeBatchLoaderRegistry(dataLoaderRegistrar);
    }

    /**
     * Creates the persisted-query cache used for Automatic Persisted Queries (APQ).
     *
     * @return a bounded in-memory persisted-query cache
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistedQueryCache persistedQueryCache() {
        log.info("Registering persisted query cache with max size {}", properties.getPersistedQueries().getMaxCacheSize());
        return new InMemoryPersistedQueryCache(properties.getPersistedQueries().getMaxCacheSize());
    }

    /**
     * Creates the interceptor implementing the Apollo Automatic Persisted Queries
     * protocol.
     *
     * <p>Only registered when {@code adhar.graphql.persisted-queries.enabled} is
     * {@code true} and Spring GraphQL server support is on the classpath.</p>
     *
     * @param persistedQueryCache the cache backing persisted query lookups
     * @return the APQ web interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.graphql.persisted-queries", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.graphql.server.WebGraphQlInterceptor")
    public PersistedQueryInterceptor persistedQueryInterceptor(PersistedQueryCache persistedQueryCache) {
        log.info("Registering Automatic Persisted Query interceptor");
        return new PersistedQueryInterceptor(persistedQueryCache);
    }

    /**
     * Creates the input validator using Jakarta Validation.
     *
     * <p>Only registered when the Jakarta Validation API is on the classpath.</p>
     *
     * @return an input validator backed by Jakarta Bean Validation
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "jakarta.validation.Validator")
    public InputValidator graphQlInputValidator() {
        log.info("Registering GraphQL input validator");
        return new InputValidator();
    }

    /**
     * Creates the GraphQL security interceptor.
     *
     * <p>Only registered when Spring Security and Spring GraphQL server support
     * are both on the classpath.</p>
     *
     * @return a security interceptor for authentication and introspection control
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {
            "org.springframework.security.core.context.SecurityContextHolder",
            "org.springframework.graphql.server.WebGraphQlInterceptor"
    })
    public GraphQlSecurityInterceptor graphQlSecurityInterceptor() {
        log.info("Registering GraphQL security interceptor - requireAuth: {}, introspection: {}",
                properties.getSecurity().isRequireAuthentication(),
                properties.isIntrospectionEnabled());
        return new GraphQlSecurityInterceptor(properties);
    }
}
