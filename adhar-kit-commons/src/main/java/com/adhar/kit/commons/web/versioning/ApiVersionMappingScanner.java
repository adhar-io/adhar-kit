package com.adhar.kit.commons.web.versioning;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

/**
 * Completes the {@link ApiVersionRegistry} once the handler mappings exist: a major the
 * application maps ({@code @GetMapping("/api/v3/...")}) but neither ships a spec for
 * nor configures still gets its {@code API-Version} header and counts as a supported
 * version - otherwise Spring would answer its requests with 400 "invalid API version".
 *
 * <p>Runs after all singletons are created and before the server accepts requests.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class ApiVersionMappingScanner implements SmartInitializingSingleton {

    private final ApiVersionRegistry registry;
    private final ListableBeanFactory beanFactory;

    /**
     * Creates the scanner.
     *
     * @param registry    the registry to complete
     * @param beanFactory where the handler mappings and the version strategy live
     */
    public ApiVersionMappingScanner(ApiVersionRegistry registry, ListableBeanFactory beanFactory) {
        this.registry = registry;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<Integer> mapped = new TreeSet<>();
        for (RequestMappingInfoHandlerMapping mapping
                : beanFactory.getBeansOfType(RequestMappingInfoHandlerMapping.class, false, false).values()) {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                for (String pattern : info.getPatternValues()) {
                    ApiPaths.majorOf(pattern).ifPresent(mapped::add);
                }
            }
        }
        mapped.forEach(registry::registerMapped);

        ApiVersionStrategy strategy = beanFactory.getBeanProvider(ApiVersionStrategy.class).getIfUnique();
        if (strategy instanceof DefaultApiVersionStrategy defaultStrategy) {
            for (ApiMajorVersion version : registry.versions()) {
                defaultStrategy.addSupportedVersion(String.valueOf(version.major()));
            }
        }
    }
}
