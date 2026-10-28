package com.adhar.kit.commons.config;

import com.adhar.kit.commons.i18n.ErrorCatalog;
import com.adhar.kit.commons.idempotency.IdempotencyAspect;
import com.adhar.kit.commons.idempotency.IdempotencyStore;
import com.adhar.kit.commons.idempotency.InMemoryIdempotencyStore;
import com.adhar.kit.commons.web.ApiVersionInterceptor;
import com.adhar.kit.commons.web.CorrelationIdFilter;
import com.adhar.kit.commons.web.ProblemDetailsResponseAdvice;
import com.adhar.kit.commons.web.SpringGlobalExceptionHandler;
import com.adhar.kit.commons.web.TenantContextFilter;
import com.adhar.kit.commons.web.versioning.AdharApiVersionDeprecationHandler;
import com.adhar.kit.commons.web.versioning.ApiVersionEndpoints;
import com.adhar.kit.commons.web.versioning.ApiVersionHeaderFilter;
import com.adhar.kit.commons.web.versioning.ApiVersionMappingScanner;
import com.adhar.kit.commons.web.versioning.ApiVersionRegistry;
import com.adhar.kit.commons.web.versioning.ApiVersioningWebMvcConfigurer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Auto-configuration for the adhar-kit-commons runtime features:
 * global exception handling (with RFC 9457 problem details for the versioned API),
 * the {@code @Idempotent} aspect, tenant / correlation context filters and API
 * versioning.
 *
 * <p>Every bean is conditional on its dependencies being on the classpath, overridable
 * via {@code @ConditionalOnMissingBean} and toggleable under {@code adhar.commons.*}
 * (see {@link AdharCommonsProperties}). The module therefore stays usable as a plain
 * library without Spring Web, AOP or the Servlet API.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(AdharCommonsProperties.class)
public class AdharCommonsAutoConfiguration {

    /**
     * Creates the localized {@link ErrorCatalog} backed by the bundled
     * {@code adhar-errors} classpath message bundles. When the application
     * exposes its own {@link org.springframework.context.MessageSource} bean it
     * is used as fallback parent, so applications can add messages for custom
     * error codes without redefining the catalog. Define your own
     * {@code ErrorCatalog} bean to replace it entirely, or disable with
     * {@code adhar.commons.error-catalog.enabled=false}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.commons.error-catalog", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public ErrorCatalog adharErrorCatalog(
            ObjectProvider<org.springframework.context.MessageSource> messageSources) {
        ErrorCatalog catalog = ErrorCatalog.withDefaults();
        org.springframework.context.MessageSource parent = messageSources.getIfUnique();
        if (parent != null
                && catalog.getMessageSource()
                    instanceof org.springframework.context.support.ResourceBundleMessageSource bundleSource) {
            bundleSource.setParentMessageSource(parent);
        }
        return catalog;
    }

    /**
     * Registers the Spring MVC global exception handler when Spring MVC and the
     * Servlet API are present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(value = {RestControllerAdvice.class, HttpServletRequest.class},
        name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnProperty(prefix = "adhar.commons.exception-handler", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    static class ExceptionHandlerConfiguration {

        /**
         * Creates the global exception handler; define your own bean to replace it.
         * When an {@link ErrorCatalog} is available it is wired in so error
         * responses are localized to the request locale.
         */
        @Bean
        @ConditionalOnMissingBean
        public SpringGlobalExceptionHandler springGlobalExceptionHandler(
                ObjectProvider<ErrorCatalog> errorCatalog) {
            SpringGlobalExceptionHandler handler = new SpringGlobalExceptionHandler();
            errorCatalog.ifAvailable(handler::setErrorCatalog);
            return handler;
        }

        /**
         * Writes error bodies as RFC 9457 problem details where
         * {@code adhar.commons.exception-handler.problem-details} asks for them
         * ({@code /api/v<major>} by default).
         */
        @Bean
        @ConditionalOnMissingBean
        public ProblemDetailsResponseAdvice problemDetailsResponseAdvice(AdharCommonsProperties properties) {
            return new ProblemDetailsResponseAdvice(properties.getExceptionHandler().getProblemDetails());
        }
    }

    /**
     * Registers the idempotency store and aspect when Spring AOP / AspectJ are present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @EnableAspectJAutoProxy
    @ConditionalOnProperty(prefix = "adhar.commons.idempotency", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    static class IdempotencyConfiguration {

        /**
         * Creates the default in-memory store; register a custom {@link IdempotencyStore}
         * bean (e.g. Redis-backed) to replace it.
         */
        @Bean
        @ConditionalOnMissingBean(IdempotencyStore.class)
        public InMemoryIdempotencyStore inMemoryIdempotencyStore() {
            return new InMemoryIdempotencyStore();
        }

        /**
         * Creates the {@code @Idempotent} aspect.
         */
        @Bean
        @ConditionalOnMissingBean
        public IdempotencyAspect idempotencyAspect(IdempotencyStore idempotencyStore) {
            return new IdempotencyAspect(idempotencyStore);
        }
    }

    /**
     * Registers the correlation-id and tenant-context servlet filters in servlet web
     * applications. The filters implement {@code Ordered}, so Spring Boot registers
     * them early in the chain.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    @ConditionalOnClass(Filter.class)
    static class ContextPropagationConfiguration {

        /**
         * Creates the correlation-id filter.
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.commons.correlation", name = "enabled",
            havingValue = "true", matchIfMissing = true)
        public CorrelationIdFilter correlationIdFilter() {
            return new CorrelationIdFilter();
        }

        /**
         * Creates the tenant-context filter.
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.commons.tenant", name = "enabled",
            havingValue = "true", matchIfMissing = true)
        public TenantContextFilter tenantContextFilter() {
            return new TenantContextFilter();
        }
    }

    /**
     * Configures API versioning for Spring MVC: the major version is the path segment of
     * {@code /api/v<major>}, resolved by Spring Framework's built-in API versioning;
     * every response below it reports the full version ({@code API-Version}); a
     * deprecated version is announced with the RFC 9745/8594 headers; and
     * {@code GET /api} plus {@code GET /api/v<major>/openapi.yaml} expose the versions
     * and the specs shipped in the artifact.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({WebMvcConfigurer.class, ApiVersionDeprecationHandler.class})
    @ConditionalOnProperty(prefix = "adhar.commons.api-versioning", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    static class ApiVersioningConfiguration {

        /**
         * The versions the service serves, from the shipped specs and
         * {@code adhar.commons.api-versioning.versions.*}.
         */
        @Bean
        @ConditionalOnMissingBean
        public ApiVersionRegistry adharApiVersionRegistry(AdharCommonsProperties properties,
                                                          ResourceLoader resourceLoader) {
            return ApiVersionRegistry.load(properties.getApiVersioning(),
                ResourcePatternUtils.getResourcePatternResolver(resourceLoader));
        }

        /**
         * Announces deprecated versions; Spring Boot hands it to the MVC version
         * strategy. Define your own {@link ApiVersionDeprecationHandler} to replace it.
         */
        @Bean
        @ConditionalOnMissingBean(ApiVersionDeprecationHandler.class)
        public AdharApiVersionDeprecationHandler adharApiVersionDeprecationHandler(
                ApiVersionRegistry registry, ObjectProvider<ApiVersionParser<?>> parser) {
            return new AdharApiVersionDeprecationHandler(registry,
                parser.getIfAvailable(SemanticApiVersionParser::new));
        }

        /**
         * Resolves the version from the path and registers the legacy interceptor.
         */
        @Bean
        public ApiVersioningWebMvcConfigurer adharApiVersioningWebMvcConfigurer(ApiVersionRegistry registry,
                Environment environment, ObjectProvider<ApiVersionInterceptor> legacyInterceptor) {
            return new ApiVersioningWebMvcConfigurer(registry, environment, legacyInterceptor);
        }

        /**
         * Sets {@code API-Version} and refuses contradicting {@code API-Version} request
         * headers under {@code /api/v<major>}.
         */
        @Bean
        @ConditionalOnMissingBean
        public ApiVersionHeaderFilter apiVersionHeaderFilter(ApiVersionRegistry registry,
                                                             AdharCommonsProperties properties) {
            return new ApiVersionHeaderFilter(registry, properties.getApiVersioning().isRejectMismatchedMajor());
        }

        /**
         * Adds the majors the application maps without a spec or configuration.
         */
        @Bean
        public ApiVersionMappingScanner adharApiVersionMappingScanner(ApiVersionRegistry registry,
                                                                      ListableBeanFactory beanFactory) {
            return new ApiVersionMappingScanner(registry, beanFactory);
        }

        /**
         * {@code GET /api} and {@code GET /api/v<major>/openapi.yaml}.
         */
        @Bean
        @ConditionalOnProperty(prefix = "adhar.commons.api-versioning.endpoints", name = "enabled",
            havingValue = "true", matchIfMissing = true)
        public RouterFunction<ServerResponse> adharApiVersionRoutes(ApiVersionRegistry registry) {
            return ApiVersionEndpoints.routes(registry);
        }

        /**
         * The legacy {@code @ApiVersion} interceptor, for paths outside
         * {@code /api/v<major>}; define your own bean to customize it.
         */
        @Bean
        @ConditionalOnMissingBean
        @SuppressWarnings("deprecation")
        public ApiVersionInterceptor apiVersionInterceptor(AdharCommonsProperties properties) {
            return new ApiVersionInterceptor(properties.getApiVersioning().isValidateRequestVersion());
        }
    }
}
