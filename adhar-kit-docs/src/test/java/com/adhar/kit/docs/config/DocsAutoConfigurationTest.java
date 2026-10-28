package com.adhar.kit.docs.config;

import com.adhar.kit.commons.web.versioning.ApiMajorVersion;
import com.adhar.kit.commons.web.versioning.ApiVersionRegistry;
import com.adhar.kit.docs.asyncapi.AsyncApiDocument;
import com.adhar.kit.docs.asyncapi.AsyncApiGenerator;
import com.adhar.kit.docs.asyncapi.AsyncApiSpecExporter;
import com.adhar.kit.docs.diff.OpenApiDiffService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DocsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocsAutoConfiguration.class));

    @Test
    void diffAndAsyncBeansAbsentByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OpenApiDiffService.class);
            assertThat(context).doesNotHaveBean(AsyncApiGenerator.class);
            assertThat(context).doesNotHaveBean(AsyncApiSpecExporter.class);
            assertThat(context).doesNotHaveBean(AsyncApiDocument.class);
        });
    }

    @Test
    void diffServiceRegisteredWhenEnabled() {
        runner.withPropertyValues("adhar.docs.diff.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OpenApiDiffService.class));
    }

    @Test
    void asyncApiBeansRegisteredWhenEnabled() {
        runner.withPropertyValues(
                        "adhar.docs.async-api.enabled=true",
                        "adhar.docs.async-api.title=Events API",
                        "adhar.docs.async-api.channels[0].name=orderCreated",
                        "adhar.docs.async-api.channels[0].address=orders.created",
                        "adhar.docs.async-api.channels[0].action=SEND_AND_RECEIVE",
                        "adhar.docs.async-api.channels[0].message-name=OrderCreated")
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncApiGenerator.class);
                    assertThat(context).hasSingleBean(AsyncApiSpecExporter.class);
                    assertThat(context).hasSingleBean(AsyncApiDocument.class);

                    AsyncApiDocument doc = context.getBean(AsyncApiDocument.class);
                    assertThat(doc.getRoot().path("info").path("title").asText())
                            .isEqualTo("Events API");
                    assertThat(doc.getRoot().path("channels").path("orderCreated")
                            .path("address").asText()).isEqualTo("orders.created");
                    assertThat(doc.getRoot().path("operations").has("orderCreatedSend")).isTrue();
                    assertThat(doc.getRoot().path("operations").has("orderCreatedReceive")).isTrue();
                });
    }

    @Test
    void docsDisabledSkipsAutoConfiguration() {
        runner.withPropertyValues("adhar.docs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenApiDiffService.class));
    }

    @Test
    void theDocumentIsNamedAfterTheApplicationAndItsBuild() {
        Properties build = new Properties();
        build.setProperty("version", "3.4.5");
        runner.withPropertyValues("spring.application.name=orders")
                .withBean(BuildProperties.class, () -> new BuildProperties(build))
                .run(context -> {
                    Info info = context.getBean(OpenAPI.class).getInfo();
                    assertThat(info.getTitle()).isEqualTo("orders");
                    assertThat(info.getVersion()).isEqualTo("3.4.5");
                });
    }

    @Test
    void explicitTitleAndVersionWinAndDefaultsRemainWithoutApplicationInformation() {
        Properties build = new Properties();
        build.setProperty("version", "3.4.5");
        runner.withPropertyValues("spring.application.name=orders", "adhar.docs.title=Orders API",
                        "adhar.docs.version=1.2.0")
                .withBean(BuildProperties.class, () -> new BuildProperties(build))
                .run(context -> {
                    Info info = context.getBean(OpenAPI.class).getInfo();
                    assertThat(info.getTitle()).isEqualTo("Orders API");
                    assertThat(info.getVersion()).isEqualTo("1.2.0");
                });
        runner.run(context -> {
            Info info = context.getBean(OpenAPI.class).getInfo();
            assertThat(info.getTitle()).isEqualTo("Adhar API Documentation");
            assertThat(info.getVersion()).isEqualTo("1.0.0");
        });
    }

    @Test
    void anApplicationsOwnOpenApiBeanReplacesTheDefault() {
        OpenAPI own = new OpenAPI().info(new Info().title("Mine").version("0.0.1"));
        runner.withBean("ownOpenApi", OpenAPI.class, () -> own)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    assertThat(context.getBean(OpenAPI.class)).isSameAs(own);
                });
    }

    @Test
    void shippedSpecsAreShownWhenAdharKitCommonsServesThem() {
        runner.withUserConfiguration(SwaggerUiSettings.class)
                .withBean(ApiVersionRegistry.class, () -> new ApiVersionRegistry(List.of(
                        new ApiMajorVersion(1, "1.4.0", null, null, null, null, null,
                                new ByteArrayResource("openapi: 3.0.3".getBytes())))))
                .run(context -> {
                    assertThat(context).hasSingleBean(ShippedSpecSwaggerUi.class);
                    assertThat(context.getBean(SwaggerUiConfigProperties.class).getUrls())
                            .extracting(SwaggerUrl::getUrl).containsExactly("/api/v1/openapi.yaml");
                });
        runner.withPropertyValues("adhar.commons.api-versioning.endpoints.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ShippedSpecSwaggerUi.class));
    }

    /**
     * springdoc's settings bean through a factory method: the class itself is a
     * conditional configuration class that needs the rest of springdoc.
     */
    @Configuration(proxyBeanMethods = false)
    static class SwaggerUiSettings {

        @Bean
        SwaggerUiConfigProperties swaggerUiConfigProperties() {
            return new SwaggerUiConfigProperties();
        }
    }
}
