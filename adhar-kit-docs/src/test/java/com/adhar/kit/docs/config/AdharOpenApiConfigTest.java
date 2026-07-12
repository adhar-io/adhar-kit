package com.adhar.kit.docs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdharOpenApiConfigTest {

    @Test
    void createOpenApiWithoutSecurityHasNoComponents() {
        OpenApiProperties props = new OpenApiProperties();
        props.getInfo().setTitle("Svc");
        props.getInfo().setVersion("1.2.3");
        props.getInfo().setDescription("desc");
        props.getSecurity().setEnabled(false);

        OpenAPI openApi = new AdharOpenApiConfig(props).createOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Svc");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.2.3");
        assertThat(openApi.getComponents()).isNull();
        assertThat(openApi.getSecurity()).isNull();
    }

    @Test
    void createOpenApiWithContactAndLicense() {
        OpenApiProperties props = new OpenApiProperties();
        OpenApiProperties.ContactInfo contact = new OpenApiProperties.ContactInfo();
        contact.setName("Team");
        contact.setEmail("team@x.com");
        contact.setUrl("https://x.com");
        props.getInfo().setContact(contact);
        OpenApiProperties.LicenseInfo license = new OpenApiProperties.LicenseInfo();
        license.setName("Apache 2.0");
        license.setUrl("https://license");
        props.getInfo().setLicense(license);

        OpenAPI openApi = new AdharOpenApiConfig(props).createOpenApi();

        assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Team");
        assertThat(openApi.getInfo().getLicense().getName()).isEqualTo("Apache 2.0");
    }

    @Test
    void createServersFromProperties() {
        OpenApiProperties props = new OpenApiProperties();
        OpenApiProperties.ServerConfig server = new OpenApiProperties.ServerConfig();
        server.setUrl("https://prod");
        server.setDescription("Production");
        props.getServers().add(server);

        OpenAPI openApi = new AdharOpenApiConfig(props).createOpenApi();

        assertThat(openApi.getServers()).hasSize(1);
        assertThat(openApi.getServers().get(0).getUrl()).isEqualTo("https://prod");
    }

    @Test
    void builderWithAllSecuritySchemes() {
        OpenAPI openApi = AdharOpenApiConfig.builder()
                .title("Full")
                .version("2.0.0")
                .description("full security")
                .contact("Name", "n@x.com", "https://n")
                .license("MIT", "https://mit")
                .server("https://srv", "Server")
                .withJwtSecurity()
                .withApiKeySecurity("X-CUSTOM-KEY")
                .withOAuth2Security()
                .withBasicAuth()
                .build();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Full");
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKeys("bearerAuth", "apiKey", "oauth2", "basicAuth");
        assertThat(openApi.getComponents().getSecuritySchemes().get("apiKey").getName())
                .isEqualTo("X-CUSTOM-KEY");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getType())
                .isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(openApi.getSecurity()).hasSize(4);
    }

    @Test
    void builderJwtOnlyProducesSingleRequirement() {
        OpenAPI openApi = AdharOpenApiConfig.builder()
                .title("Jwt")
                .version("1.0.0")
                .withJwtSecurity()
                .build();

        assertThat(openApi.getComponents().getSecuritySchemes()).containsOnlyKeys("bearerAuth");
        assertThat(openApi.getSecurity()).hasSize(1);
    }
}
