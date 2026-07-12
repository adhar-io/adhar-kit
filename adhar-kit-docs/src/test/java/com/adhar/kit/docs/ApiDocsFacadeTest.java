package com.adhar.kit.docs;

import com.adhar.kit.docs.api.ApiDocsService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiDocsFacadeTest {

    private ApiDocsFacade facade;

    @BeforeEach
    void setUp() {
        ApiDocsFacade.resetInstance();
        facade = ApiDocsFacade.getInstance();
        // Point at a closed port so HTTP fetches fail fast and deterministically.
        facade.withBaseUrl("http://127.0.0.1:1");
    }

    @Test
    void getInstanceReturnsSingleton() {
        assertSame(facade, ApiDocsFacade.getInstance());
    }

    @Test
    void defaultMetadataValues() {
        assertThat(facade.getTitle()).isEqualTo("API Documentation");
        assertThat(facade.getVersion()).isEqualTo("1.0.0");
        assertThat(facade.getDescription()).isEqualTo("API Documentation");
        assertTrue(facade.isEnabled());
    }

    @Test
    void settersUpdateMetadata() {
        facade.setTitle("Payments");
        facade.setVersion("2.0.0");
        facade.setDescription("Handles payments");
        facade.setTermsOfService("https://tos");
        facade.setContact("Team", "team@x.com", "https://x.com");
        facade.setLicense("Apache 2.0", "https://license");

        assertThat(facade.getTitle()).isEqualTo("Payments");
        assertThat(facade.getVersion()).isEqualTo("2.0.0");
        assertThat(facade.getDescription()).isEqualTo("Handles payments");

        Map<String, String> info = facade.getApiInfo();
        assertThat(info).containsEntry("title", "Payments")
                .containsEntry("version", "2.0.0")
                .containsEntry("description", "Handles payments")
                .containsEntry("contactName", "Team")
                .containsEntry("contactEmail", "team@x.com")
                .containsEntry("contactUrl", "https://x.com")
                .containsEntry("licenseName", "Apache 2.0")
                .containsEntry("licenseUrl", "https://license")
                .containsEntry("termsOfService", "https://tos");
    }

    @Test
    void apiInfoOmitsNullOptionalFields() {
        Map<String, String> info = facade.getApiInfo();
        assertThat(info).containsKeys("title", "version", "description");
        assertThat(info).doesNotContainKeys("contactName", "licenseName", "termsOfService");
    }

    @Test
    void setEnabledTogglesFlag() {
        facade.setEnabled(false);
        assertFalse(facade.isEnabled());
        facade.setEnabled(true);
        assertTrue(facade.isEnabled());
    }

    @Test
    void builderStyleMethodsChainAndApply() {
        ApiDocsFacade result = facade
                .withTitle("T")
                .withVersion("9.9")
                .withDescription("D")
                .withApiDocsPath("/custom/api-docs")
                .withApiDocsYamlPath("/custom/api-docs.yaml")
                .withSwaggerUiPath("/custom/swagger");
        assertSame(facade, result);
        assertThat(facade.getTitle()).isEqualTo("T");
        assertThat(facade.getVersion()).isEqualTo("9.9");
        assertThat(facade.getSwaggerUiUrl()).isEqualTo("http://127.0.0.1:1/custom/swagger");
    }

    @Test
    void addTagAndGetTags() {
        facade.addTag("Orders", "Order operations");
        assertThat(facade.getTags()).containsEntry("Orders", "Order operations");
    }

    @Test
    void addServerStored() {
        facade.addServer("https://prod", "Production");
        List<ApiDocsFacade.ServerEntry> servers = facade.getServers();
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).url()).isEqualTo("https://prod");
        assertThat(servers.get(0).description()).isEqualTo("Production");
    }

    @Test
    void addSecuritySchemeViaEnumType() {
        facade.addSecurityScheme("bearer", ApiDocsService.SecuritySchemeType.HTTP, "bearer", "JWT");
        List<ApiDocsFacade.SecuritySchemeEntry> schemes = facade.getSecuritySchemes();
        assertThat(schemes).hasSize(1);
        assertThat(schemes.get(0).name()).isEqualTo("bearer");
        assertThat(schemes.get(0).type()).isEqualTo("HTTP");
        assertThat(schemes.get(0).bearerFormat()).isEqualTo("JWT");
    }

    @Test
    void addSecuritySchemeViaStringTypeReturnsFacade() {
        ApiDocsFacade result = facade.addSecurityScheme("apiKey", "apiKey", "header");
        assertSame(facade, result);
        assertThat(facade.getSecuritySchemes()).hasSize(1);
        assertThat(facade.getSecuritySchemes().get(0).bearerFormat()).isNull();
    }

    @Test
    void addGlobalHeaderReturnsFacade() {
        ApiDocsFacade result = facade.addGlobalHeader("X-Tenant-ID", "Tenant id");
        assertSame(facade, result);
        assertThat(facade.getGlobalHeaders()).hasSize(1);
        assertThat(facade.getGlobalHeaders().get(0).name()).isEqualTo("X-Tenant-ID");
    }

    @Test
    void getOpenApiSpecFallsBackToLocallyBuiltJson() {
        facade.setTitle("My API");
        facade.setVersion("3.0.0");
        facade.setContact("C", "c@x.com", "https://c");
        facade.addSecurityScheme("bearerAuth", ApiDocsService.SecuritySchemeType.HTTP, "bearer", "JWT");

        String spec = facade.getOpenApiSpec();
        assertThat(spec).contains("\"openapi\": \"3.0.1\"");
        assertThat(spec).contains("\"title\": \"My API\"");
        assertThat(spec).contains("\"version\": \"3.0.0\"");
        assertThat(spec).contains("\"contact\"");
        assertThat(spec).contains("bearerAuth");
        assertThat(spec).contains("\"bearerFormat\": \"JWT\"");
        // getOpenApiJson delegates to getOpenApiSpec
        assertThat(facade.getOpenApiJson()).isEqualTo(spec);
    }

    @Test
    void getOpenApiSpecYamlFallsBackToLocallyBuiltYaml() {
        facade.setTitle("Yaml API");
        facade.setContact("Owner", "o@x.com", "https://o");
        facade.addSecurityScheme("apiKey", "apiKey", "header");

        String yaml = facade.getOpenApiSpecYaml();
        assertThat(yaml).contains("openapi: '3.0.1'");
        assertThat(yaml).contains("title: 'Yaml API'");
        assertThat(yaml).contains("contact:");
        assertThat(yaml).contains("apiKey:");
        assertThat(facade.getOpenApiYaml()).isEqualTo(yaml);
    }

    @Test
    void fallbackJsonEscapesSpecialCharacters() {
        facade.setTitle("Quote\"And\\Backslash");
        String spec = facade.getOpenApiSpec();
        assertThat(spec).contains("Quote\\\"And\\\\Backslash");
    }

    @Test
    void getEndpointsEmptyWhenNoPaths() {
        // Fallback spec contains empty paths -> no endpoints.
        assertThat(facade.getEndpoints()).isEmpty();
    }

    @Test
    void getEndpointsParsesPathsFromInjectedBean() {
        String json = "{\"openapi\":\"3.0.1\","
                + "\"paths\":{"
                + "\"/api/orders\":{\"get\":{\"summary\":\"List orders\"},\"post\":{\"summary\":\"Create order\"}},"
                + "\"/api/items/{id}\":{\"delete\":{\"summary\":\"Remove item\"}}"
                + "}}";
        facade.setOpenApiResource(new FakeOpenApiResource(json));

        List<Map<String, String>> endpoints = facade.getEndpoints();
        assertThat(endpoints).hasSize(3);
        assertThat(endpoints).anySatisfy(e -> {
            assertThat(e.get("method")).isEqualTo("GET");
            assertThat(e.get("path")).isEqualTo("/api/orders");
            assertThat(e.get("summary")).isEqualTo("List orders");
        });
        assertThat(endpoints).anySatisfy(e -> {
            assertThat(e.get("method")).isEqualTo("DELETE");
            assertThat(e.get("path")).isEqualTo("/api/items/{id}");
        });
    }

    @Test
    void getOpenApiSpecUsesInjectedBeanWhenItContainsOpenapi() {
        String json = "{\"openapi\":\"3.0.1\",\"paths\":{}}";
        facade.setOpenApiResource(new FakeOpenApiResource(json));
        assertThat(facade.getOpenApiSpec()).isEqualTo(json);
    }

    @Test
    void isSwaggerUiEnabledFalseWhenUnreachable() {
        assertFalse(facade.isSwaggerUiEnabled());
    }

    @Test
    void createOpenApiCustomizerNotNullWhenSpringDocPresent() {
        Object customizer = facade.createOpenApiCustomizer();
        assertNotNull(customizer);
        assertThat(customizer).isInstanceOf(org.springdoc.core.customizers.OpenApiCustomizer.class);
    }

    @Test
    void createOperationCustomizerNotNullWhenSpringDocPresent() {
        Object customizer = facade.createOperationCustomizer();
        assertNotNull(customizer);
        assertThat(customizer).isInstanceOf(org.springdoc.core.customizers.OperationCustomizer.class);
    }

    @Test
    void openApiCustomizerAdapterAppliesAllFacadeState() {
        facade.setTitle("Adapter API");
        facade.setVersion("7.0.0");
        facade.setDescription("desc");
        facade.setContact("Name", "n@x.com", "https://n");
        facade.setLicense("MIT", "https://mit");
        facade.setTermsOfService("https://tos");
        facade.addServer("https://srv", "server");
        facade.addTag("Tag1", "first tag");
        facade.addSecurityScheme("bearer", ApiDocsService.SecuritySchemeType.HTTP, "bearer", "JWT");
        facade.addSecurityScheme("key", "apikey", null);
        facade.addSecurityScheme("oauth", "oauth2", null);
        facade.addSecurityScheme("oidc", "openidconnect", null);

        org.springdoc.core.customizers.OpenApiCustomizer customizer =
                (org.springdoc.core.customizers.OpenApiCustomizer) facade.createOpenApiCustomizer();

        OpenAPI openApi = new OpenAPI();
        customizer.customise(openApi);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Adapter API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("7.0.0");
        assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Name");
        assertThat(openApi.getInfo().getLicense().getName()).isEqualTo("MIT");
        assertThat(openApi.getInfo().getTermsOfService()).isEqualTo("https://tos");
        assertThat(openApi.getServers()).hasSize(1);
        assertThat(openApi.getTags()).hasSize(1);

        Components components = openApi.getComponents();
        assertThat(components.getSecuritySchemes()).containsKeys("bearer", "key", "oauth", "oidc");
        assertThat(components.getSecuritySchemes().get("bearer").getType())
                .isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(components.getSecuritySchemes().get("key").getType())
                .isEqualTo(SecurityScheme.Type.APIKEY);
        assertThat(components.getSecuritySchemes().get("oauth").getType())
                .isEqualTo(SecurityScheme.Type.OAUTH2);
        assertThat(components.getSecuritySchemes().get("oidc").getType())
                .isEqualTo(SecurityScheme.Type.OPENIDCONNECT);
    }

    @Test
    void operationCustomizerAdapterAddsGlobalHeaders() {
        facade.addGlobalHeader("X-Tenant-ID", "Tenant");
        facade.addGlobalHeader("X-Region", "Region");

        org.springdoc.core.customizers.OperationCustomizer customizer =
                (org.springdoc.core.customizers.OperationCustomizer) facade.createOperationCustomizer();

        Operation operation = new Operation();
        HandlerMethod handlerMethod = null;
        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getParameters()).hasSize(2);
        assertThat(result.getParameters()).anySatisfy(p -> {
            assertThat(p.getName()).isEqualTo("X-Tenant-ID");
            assertThat(p.getIn()).isEqualTo("header");
            assertThat(p.getRequired()).isFalse();
        });
    }

    /**
     * Fake stand-in for SpringDoc's OpenApiResource exposing an openapiJson method
     * that the facade discovers and invokes reflectively.
     */
    public static class FakeOpenApiResource {
        private final String json;

        FakeOpenApiResource(String json) {
            this.json = json;
        }

        public String openapiJson(Object request) {
            return json;
        }
    }
}
