package com.adhar.kit.commons.config;

import com.adhar.kit.commons.i18n.ErrorCatalog;
import com.adhar.kit.commons.idempotency.IdempotencyAspect;
import com.adhar.kit.commons.idempotency.IdempotencyStore;
import com.adhar.kit.commons.idempotency.InMemoryIdempotencyStore;
import com.adhar.kit.commons.web.ApiVersionInterceptor;
import com.adhar.kit.commons.web.CorrelationIdFilter;
import com.adhar.kit.commons.web.SpringGlobalExceptionHandler;
import com.adhar.kit.commons.web.TenantContextFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
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
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the adhar-kit-commons runtime features:
 * global exception handling, the {@code @Idempotent} aspect, tenant / correlation
 * context filters and the {@code @ApiVersion} interceptor.
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
     * Registers the Spring MVC global exception handler when Spring Web and the
     * Servlet API are present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RestControllerAdvice.class, HttpServletRequest.class})
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
     * Registers the {@code @ApiVersion} interceptor with Spring MVC.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnProperty(prefix = "adhar.commons.api-versioning", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    static class ApiVersioningConfiguration implements WebMvcConfigurer {

        private final ObjectProvider<ApiVersionInterceptor> interceptorProvider;

        ApiVersioningConfiguration(ObjectProvider<ApiVersionInterceptor> interceptorProvider) {
            this.interceptorProvider = interceptorProvider;
        }

        /**
         * Creates the interceptor; define your own bean to customize it.
         */
        @Bean
        @ConditionalOnMissingBean
        public ApiVersionInterceptor apiVersionInterceptor(AdharCommonsProperties properties) {
            return new ApiVersionInterceptor(properties.getApiVersioning().isValidateRequestVersion());
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            ApiVersionInterceptor interceptor = interceptorProvider.getIfAvailable();
            if (interceptor != null) {
                registry.addInterceptor(interceptor);
            }
        }
    }
}
