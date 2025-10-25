package com.adhar.apidocs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for API documentation using SpringDoc OpenAPI.
 */
@AutoConfiguration
@EnableConfigurationProperties(ApiDocsProperties.class)
@ConditionalOnProperty(prefix = "adhar.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiDocsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI customOpenAPI(ApiDocsProperties properties,
                                 @Value("${spring.application.name:application}") String applicationName) {
        
        return new OpenAPI()
                .info(createInfo(properties, applicationName))
                .servers(createServers(properties));
    }

    private Info createInfo(ApiDocsProperties properties, String applicationName) {
        String title = properties.getTitle() != null ? properties.getTitle() : applicationName;
        
        Info info = new Info()
                .title(title)
                .version(properties.getVersion())
                .description(properties.getDescription());
        
        if (properties.getContact() != null) {
            info.contact(new Contact()
                    .name(properties.getContact().getName())
                    .email(properties.getContact().getEmail())
                    .url(properties.getContact().getUrl()));
        }
        
        if (properties.getLicense() != null) {
            info.license(new License()
                    .name(properties.getLicense().getName())
                    .url(properties.getLicense().getUrl()));
        }
        
        return info;
    }

    private List<Server> createServers(ApiDocsProperties properties) {
        List<Server> servers = new ArrayList<>();
        
        if (properties.getServers() != null && !properties.getServers().isEmpty()) {
            properties.getServers().forEach(serverProps -> {
                Server server = new Server();
                server.setUrl(serverProps.getUrl());
                server.setDescription(serverProps.getDescription());
                servers.add(server);
            });
        }
        
        return servers;
    }
}