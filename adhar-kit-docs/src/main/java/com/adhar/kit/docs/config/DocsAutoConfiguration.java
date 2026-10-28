package com.adhar.kit.docs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import com.adhar.kit.docs.asyncapi.AsyncApiDocument;
import com.adhar.kit.docs.asyncapi.AsyncApiGenerator;
import com.adhar.kit.docs.asyncapi.AsyncApiSpecExporter;
import com.adhar.kit.docs.diff.OpenApiDiffService;
import com.adhar.kit.commons.web.versioning.ApiVersionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Auto-configuration for Adhar API Documentation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DocsProperties.class)
@ConditionalOnProperty(prefix = "adhar.docs", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DocsAutoConfiguration {

    private final DocsProperties properties;

    /**
     * The base document springdoc builds {@code /v3/api-docs} on. Its title is
     * {@code adhar.docs.title} when set, else the application's name; its version
     * {@code adhar.docs.version} when set, else the build's version (build-info), so
     * the document names the service it describes rather than a generic default.
     * Define your own {@link OpenAPI} bean to replace it.
     *
     * @param environment     to tell explicit settings from defaults
     * @param buildProperties the build information, when the build records it
     * @return the base document
     */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI customOpenAPI(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        log.info("Initializing OpenAPI documentation");

        var apiInfo = properties.getApiInfo();
        String title = environment.containsProperty("adhar.docs.title")
                ? apiInfo.getTitle()
                : environment.getProperty("spring.application.name", apiInfo.getTitle());
        BuildProperties build = buildProperties.getIfAvailable();
        String version = environment.containsProperty("adhar.docs.version") || build == null
                || build.getVersion() == null
                ? apiInfo.getVersion()
                : build.getVersion();

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(apiInfo.getDescription())
                        .version(version)
                        .termsOfService(apiInfo.getTermsOfService())
                        .contact(new Contact()
                                .name(apiInfo.getContact().getName())
                                .email(apiInfo.getContact().getEmail())
                                .url(apiInfo.getContact().getUrl()))
                        .license(new License()
                                .name(apiInfo.getLicense().getName())
                                .url(apiInfo.getLicense().getUrl())));

        // Add security scheme if enabled
        if (properties.getSecurity().isEnabled()) {
            var security = properties.getSecurity();

            openAPI.components(new Components()
                    .addSecuritySchemes(security.getSchemeName(),
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme(security.getScheme())
                                    .bearerFormat(security.getBearerFormat())
                                    .description(security.getDescription())))
                    .addSecurityItem(new SecurityRequirement().addList(security.getSchemeName()));
        }

        return openAPI;
    }

    @Bean
    @ConditionalOnProperty(name = "adhar.docs.groups[0].name")
    public List<GroupedOpenApi> groupedOpenApis() {
        log.info("Configuring API groups");

        return properties.getGroups().stream()
                .map(group -> GroupedOpenApi.builder()
                        .group(group.getName())
                        .displayName(group.getDisplayName())
                        .packagesToScan(group.getPackagesToScan())
                        .pathsToMatch(group.getPathsToMatch())
                        .build())
                .toList();
    }

    /**
     * Registers the OpenAPI breaking-change diff service for use as a CI gate.
     *
     * <p>Enabled via {@code adhar.docs.diff.enabled=true}.</p>
     *
     * @return the diff service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.docs.diff", name = "enabled", havingValue = "true")
    public OpenApiDiffService openApiDiffService() {
        log.info("Registering OpenApiDiffService (breaking-change CI gate)");
        return new OpenApiDiffService();
    }

    /**
     * Registers the AsyncAPI generator.
     *
     * <p>Enabled via {@code adhar.docs.async-api.enabled=true}.</p>
     *
     * @return the AsyncAPI generator
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.docs.async-api", name = "enabled", havingValue = "true")
    public AsyncApiGenerator asyncApiGenerator() {
        log.info("Registering AsyncApiGenerator");
        return new AsyncApiGenerator();
    }

    /**
     * Registers the AsyncAPI spec exporter (json + yaml).
     *
     * @return the AsyncAPI exporter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.docs.async-api", name = "enabled", havingValue = "true")
    public AsyncApiSpecExporter asyncApiSpecExporter() {
        return new AsyncApiSpecExporter();
    }

    /**
     * Builds an {@link AsyncApiDocument} from the configured channel metadata.
     *
     * @param generator the AsyncAPI generator
     * @return the generated AsyncAPI document
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.docs.async-api", name = "enabled", havingValue = "true")
    public AsyncApiDocument asyncApiDocument(AsyncApiGenerator generator) {
        DocsProperties.AsyncApi cfg = properties.getAsyncApi();
        List<AsyncApiGenerator.ChannelDefinition> channels = cfg.getChannels().stream()
                .map(c -> new AsyncApiGenerator.ChannelDefinition(
                        c.getName(),
                        c.getAddress(),
                        c.getDescription(),
                        AsyncApiGenerator.Action.from(c.getAction()),
                        c.getMessageName(),
                        c.getPayloadType()))
                .toList();
        log.info("Building AsyncApiDocument with {} channel(s)", channels.size());
        return generator.generate(cfg.getTitle(), cfg.getVersion(), cfg.getDescription(), channels);
    }

    /**
     * Shows the specs the service ships in Swagger UI, when adhar-kit-commons serves
     * them ({@code GET /api/v<major>/openapi.yaml}).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "com.adhar.kit.commons.web.versioning.ApiVersionRegistry",
            "org.springdoc.core.properties.SwaggerUiConfigProperties"})
    @ConditionalOnProperty(prefix = "adhar.commons.api-versioning.endpoints", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class ShippedSpecConfiguration {

        /**
         * Points Swagger UI at the shipped specs.
         *
         * @param registry   the versions and their specs
         * @param swaggerUi  springdoc's Swagger UI settings
         * @param properties the docs properties
         * @return the configurer
         */
        @Bean
        @ConditionalOnMissingBean
        public ShippedSpecSwaggerUi adharShippedSpecSwaggerUi(ObjectProvider<ApiVersionRegistry> registry,
                                                            ObjectProvider<SwaggerUiConfigProperties> swaggerUi,
                                                            DocsProperties properties) {
            return new ShippedSpecSwaggerUi(registry, swaggerUi, properties);
        }
    }
}

