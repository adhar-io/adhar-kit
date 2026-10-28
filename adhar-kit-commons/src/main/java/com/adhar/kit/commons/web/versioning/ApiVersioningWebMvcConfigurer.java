package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.web.ApiVersionInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Turns on Spring Framework 7's built-in API versioning with the platform's defaults:
 * the version is the major in the path ({@code /api/v<major>/...}), resolved only for
 * paths of that shape, so actuator, {@code /dapr/*} and every other unversioned path
 * neither needs nor gets a version.
 *
 * <p>Requests are not required to carry a version unless
 * {@code spring.mvc.apiversion.required} says so, and the majors the service serves are
 * the supported versions. Controllers may still declare {@code version = "..."} on
 * their mappings; Spring routes on it as usual.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class ApiVersioningWebMvcConfigurer implements WebMvcConfigurer {

    /** Index of the version segment in {@code /api/v<major>/...}. */
    static final int VERSION_SEGMENT = 1;

    private final ApiVersionRegistry registry;
    private final Environment environment;
    private final ObjectProvider<ApiVersionInterceptor> legacyInterceptor;

    /**
     * Creates the configurer.
     *
     * @param registry          the versions the service serves
     * @param environment       to tell whether {@code spring.mvc.apiversion.required} is set
     * @param legacyInterceptor the {@code @ApiVersion} interceptor for unversioned paths, if any
     */
    public ApiVersioningWebMvcConfigurer(ApiVersionRegistry registry, Environment environment,
                                         ObjectProvider<ApiVersionInterceptor> legacyInterceptor) {
        this.registry = registry;
        this.environment = environment;
        this.legacyInterceptor = legacyInterceptor;
    }

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(VERSION_SEGMENT,
                path -> ApiPaths.majorOf(path.pathWithinApplication().value()).isPresent());
        if (!environment.containsProperty("spring.mvc.apiversion.required")) {
            // Spring's default (required as soon as a resolver exists) would turn every
            // request outside /api/v<major> into a 400 missing-version error.
            configurer.setVersionRequired(false);
        }
        for (ApiMajorVersion version : registry.versions()) {
            configurer.addSupportedVersions(String.valueOf(version.major()));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void addInterceptors(InterceptorRegistry interceptors) {
        legacyInterceptor.ifAvailable(interceptors::addInterceptor);
    }
}
