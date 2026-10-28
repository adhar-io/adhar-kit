package com.adhar.kit.docs.config;

import com.adhar.kit.commons.web.versioning.ApiMajorVersion;
import com.adhar.kit.commons.web.versioning.ApiVersionRegistry;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests for {@link ShippedSpecSwaggerUi}.
 */
class ShippedSpecSwaggerUiTest {

    private static final ByteArrayResource SPEC = new ByteArrayResource("openapi: 3.0.3".getBytes());

    @Test
    void swaggerUiListsOneEntryPerShippedSpecWithTheHighestMajorSelected() {
        SwaggerUiConfigProperties ui = new SwaggerUiConfigProperties();
        apply(ui, new DocsProperties(), new ApiVersionRegistry(List.of(
                new ApiMajorVersion(1, "1.9.0", null, null, null, null, null, SPEC),
                new ApiMajorVersion(2, "2.0.3", null, null, null, null, null, SPEC),
                ApiMajorVersion.of(3, "3.0.0"))));

        assertThat(ui.getUrls()).extracting(SwaggerUrl::getName, SwaggerUrl::getUrl, SwaggerUrl::getDisplayName)
                .containsExactly(
                        tuple("v1", "/api/v1/openapi.yaml", "v1 (1.9.0)"),
                        tuple("v2", "/api/v2/openapi.yaml", "v2 (2.0.3)"));
        assertThat(ui.getUrlsPrimaryName()).isEqualTo("v2 (2.0.3)");
    }

    @Test
    void explicitSwaggerUiUrlsAreLeftAlone() {
        SwaggerUiConfigProperties single = new SwaggerUiConfigProperties();
        single.setUrl("/custom.yaml");
        apply(single, new DocsProperties(), registryWithSpec());
        assertThat(single.getUrls()).isNullOrEmpty();

        SwaggerUiConfigProperties several = new SwaggerUiConfigProperties();
        several.setUrls(Set.of(new SwaggerUrl("mine", "/mine.yaml", "Mine")));
        apply(several, new DocsProperties(), registryWithSpec());
        assertThat(several.getUrls()).extracting(SwaggerUrl::getName).containsExactly("mine");
    }

    @Test
    void nothingChangesWithoutShippedSpecsOrWhenSwitchedOff() {
        SwaggerUiConfigProperties ui = new SwaggerUiConfigProperties();
        apply(ui, new DocsProperties(), new ApiVersionRegistry(List.of(ApiMajorVersion.of(1, "1.0.0"))));
        assertThat(ui.getUrls()).isNullOrEmpty();

        DocsProperties off = new DocsProperties();
        off.setShippedSpecs(false);
        apply(ui, off, registryWithSpec());
        assertThat(ui.getUrls()).isNullOrEmpty();

        // No registry (adhar-kit-commons versioning off) and no Swagger UI are both fine.
        DefaultListableBeanFactory empty = new DefaultListableBeanFactory();
        new ShippedSpecSwaggerUi(empty.getBeanProvider(ApiVersionRegistry.class),
                empty.getBeanProvider(SwaggerUiConfigProperties.class), new DocsProperties())
                .afterSingletonsInstantiated();
    }

    private static ApiVersionRegistry registryWithSpec() {
        return new ApiVersionRegistry(List.of(new ApiMajorVersion(1, "1.0.0", null, null, null, null, null, SPEC)));
    }

    private static void apply(SwaggerUiConfigProperties ui, DocsProperties properties, ApiVersionRegistry registry) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("ui", ui);
        beans.registerSingleton("registry", registry);
        new ShippedSpecSwaggerUi(beans.getBeanProvider(ApiVersionRegistry.class),
                beans.getBeanProvider(SwaggerUiConfigProperties.class), properties).afterSingletonsInstantiated();
    }
}
