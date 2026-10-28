package com.adhar.kit.docs.config;

import com.adhar.kit.commons.web.versioning.ApiMajorVersion;
import com.adhar.kit.commons.web.versioning.ApiVersionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes the OpenAPI documents the service ships the documentation Swagger UI shows.
 *
 * <p>A spec-first service generates its code from {@code api/v<major>/openapi.yaml},
 * and adhar-kit-commons serves that file at {@code /api/v<major>/openapi.yaml}. That
 * file is the contract; the document springdoc derives from the code is an
 * approximation of it (no descriptions, examples or problem schemas). So when specs are
 * shipped, Swagger UI lists one entry per major - the highest selected - pointing at
 * them. springdoc's {@code /v3/api-docs} stays available for tools.</p>
 *
 * <p>Nothing changes when {@code springdoc.swagger-ui.url} or {@code .urls} is
 * configured, when no spec is shipped, or with {@code adhar.docs.shipped-specs=false}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class ShippedSpecSwaggerUi implements SmartInitializingSingleton {

    private final ObjectProvider<ApiVersionRegistry> registry;
    private final ObjectProvider<SwaggerUiConfigProperties> swaggerUi;
    private final DocsProperties properties;

    /**
     * Creates the configurer.
     *
     * @param registry   the versions and their shipped specs (adhar-kit-commons)
     * @param swaggerUi  springdoc's Swagger UI settings
     * @param properties the docs properties
     */
    public ShippedSpecSwaggerUi(ObjectProvider<ApiVersionRegistry> registry,
                                ObjectProvider<SwaggerUiConfigProperties> swaggerUi, DocsProperties properties) {
        this.registry = registry;
        this.swaggerUi = swaggerUi;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        SwaggerUiConfigProperties ui = swaggerUi.getIfAvailable();
        ApiVersionRegistry versions = registry.getIfAvailable();
        if (!properties.isShippedSpecs() || ui == null || versions == null
                || StringUtils.hasText(ui.getUrl()) || !CollectionUtils.isEmpty(ui.getUrls())) {
            return;
        }
        Set<SwaggerUrl> urls = new LinkedHashSet<>();
        String primary = null;
        for (ApiMajorVersion version : versions.versions()) {
            if (version.spec() == null) {
                continue;
            }
            String name = "v" + version.major();
            String displayName = name + " (" + version.version() + ")";
            urls.add(new SwaggerUrl(name, version.specPath(), displayName));
            // Swagger UI selects the entry whose displayed name equals urls.primaryName.
            primary = displayName;
        }
        if (urls.isEmpty()) {
            return;
        }
        ui.setUrls(urls);
        ui.setUrlsPrimaryName(primary);
        log.info("Swagger UI shows the shipped OpenAPI specs: {}",
                urls.stream().map(SwaggerUrl::getUrl).toList());
    }
}
