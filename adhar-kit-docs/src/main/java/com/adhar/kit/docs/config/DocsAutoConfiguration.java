package com.adhar.kit.docs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public OpenAPI customOpenAPI() {
        log.info("Initializing OpenAPI documentation");

        var apiInfo = properties.getApiInfo();

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title(apiInfo.getTitle())
                        .description(apiInfo.getDescription())
                        .version(apiInfo.getVersion())
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
}

