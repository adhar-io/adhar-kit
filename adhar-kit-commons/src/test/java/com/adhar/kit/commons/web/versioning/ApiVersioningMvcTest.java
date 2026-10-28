package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.annotation.ApiVersion;
import com.adhar.kit.commons.config.AdharCommonsAutoConfiguration;
import com.adhar.kit.commons.exception.ResourceNotFoundException;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The versioning contract end to end, through Spring Boot's own Web MVC
 * auto-configuration: Spring's API version strategy, the kit's header filter,
 * deprecation handler, discovery endpoints and problem details.
 */
@SuppressWarnings("deprecation")
class ApiVersioningMvcTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    AdharCommonsAutoConfiguration.class))
            .withUserConfiguration(PetsController.class)
            .withPropertyValues(
                    "adhar.commons.api-versioning.spec-locations=classpath*:versioning/api/v*/openapi.yaml");

    @Test
    void everyResponseUnderAMajorReportsTheVersionOfItsShippedSpec() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/api/v1/pets/7"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("API-Version", "1.2.0"))
                    .andExpect(header().doesNotExist("Deprecation"))
                    .andExpect(header().doesNotExist("Sunset"));
            mvc.perform(get("/api/v2/pets/7"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("API-Version", "2.0.1"));
            // Unversioned paths are neither versioned nor refused for lacking a version.
            mvc.perform(get("/hello"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("API-Version"));
        });
    }

    @Test
    void aDeprecatedVersionAnnouncesItsDeprecationSunsetAndSuccessor() {
        runner.withPropertyValues(
                        "adhar.commons.api-versioning.versions.v1.deprecated=2020-01-01",
                        "adhar.commons.api-versioning.versions.v1.sunset=2030-01-31T23:59:59Z",
                        "adhar.commons.api-versioning.versions.v1.deprecation-link=https://docs.example.com/v1")
                .run(context -> {
                    MockMvc mvc = mvc(context);
                    mvc.perform(get("/api/v1/pets/7"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Deprecation", "@1577836800"))
                            .andExpect(header().string("Sunset", "Thu, 31 Jan 2030 23:59:59 GMT"))
                            .andExpect(header().stringValues("Link", containsInAnyOrder(
                                    "<https://docs.example.com/v1>; rel=\"deprecation\"; type=\"text/html\"",
                                    "</api/v2>; rel=\"successor-version\"")));
                    mvc.perform(get("/api/v2/pets/7"))
                            .andExpect(header().doesNotExist("Deprecation"))
                            .andExpect(header().doesNotExist("Link"));
                });
    }

    @Test
    void aVersionDeprecatedWithoutADateIsDeprecatedSinceTheEpoch() {
        runner.withPropertyValues("adhar.commons.api-versioning.versions.v2.deprecated=true",
                        "adhar.commons.api-versioning.versions.v2.successor=https://pets.example.com/api/v3")
                .run(context -> mvc(context).perform(get("/api/v2/pets/7"))
                        .andExpect(header().string("Deprecation", "@0"))
                        .andExpect(header().string("Link", "<https://pets.example.com/api/v3>; rel=\"successor-version\"")));
    }

    @Test
    void anApiVersionHeaderNamingAnotherMajorIsRefusedAsAProblem() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/api/v1/pets/7").header("API-Version", "2.0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("API-Version", "1.2.0"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").value(containsString("asks for major version 2")))
                    .andExpect(jsonPath("$.instance").value("/api/v1/pets/7"))
                    .andExpect(jsonPath("$.requested").value("2.0"))
                    .andExpect(jsonPath("$.supported").value("1.2.0"));

            mvc.perform(get("/api/v1/pets/7").header("API-Version", "banana"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("is not a version")));

            // Any version of the same major is fine: minor and patch never change the path.
            mvc.perform(get("/api/v1/pets/7").header("API-Version", "1.0")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/pets/7").header("API-Version", "v1")).andExpect(status().isOk());
        });
        runner.withPropertyValues("adhar.commons.api-versioning.reject-mismatched-major=false")
                .run(context -> mvc(context).perform(get("/api/v1/pets/7").header("API-Version", "2"))
                        .andExpect(status().isOk()));
    }

    @Test
    void theIndexListsEveryVersionTheServiceServes() {
        runner.withPropertyValues("adhar.commons.api-versioning.versions.v1.deprecated=2020-01-01",
                        "adhar.commons.api-versioning.versions.v1.sunset=2099-01-01",
                        "adhar.commons.api-versioning.versions.v2.deprecated=2099-01-01")
                .run(context -> mvc(context).perform(get("/api"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.length()").value(3))
                        .andExpect(jsonPath("$[0].major").value(1))
                        .andExpect(jsonPath("$[0].version").value("1.2.0"))
                        .andExpect(jsonPath("$[0].status").value("deprecated"))
                        .andExpect(jsonPath("$[0].sunset").value("2099-01-01T00:00:00Z"))
                        .andExpect(jsonPath("$[0].spec").value("/api/v1/openapi.yaml"))
                        // Deprecation announced for the future: still current.
                        .andExpect(jsonPath("$[1].status").value("current"))
                        .andExpect(jsonPath("$[1].sunset").value(nullValue()))
                        // Mapped by a controller, no spec, no configuration.
                        .andExpect(jsonPath("$[2].major").value(3))
                        .andExpect(jsonPath("$[2].version").value("3.0.0"))
                        .andExpect(jsonPath("$[2].spec").value(nullValue())));
    }

    @Test
    void theShippedSpecIsServedWithAnETag() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            byte[] shipped = new ClassPathResource("versioning/api/v1/openapi.yaml").getContentAsByteArray();
            MvcResult result = mvc.perform(get("/api/v1/openapi.yaml"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_YAML))
                    .andExpect(content().bytes(shipped))
                    .andExpect(header().string("Cache-Control", "no-cache"))
                    .andExpect(header().string("API-Version", "1.2.0"))
                    .andReturn();
            String etag = result.getResponse().getHeader("ETag");
            assertThat(etag).startsWith("\"").endsWith("\"");

            mvc.perform(get("/api/v1/openapi.yaml").header("If-None-Match", etag))
                    .andExpect(status().isNotModified());
            // v3 ships no spec, so nothing is served for it.
            mvc.perform(get("/api/v3/openapi.yaml")).andExpect(status().isNotFound());
        });
    }

    @Test
    void aMajorMappedWithoutSpecOrConfigurationIsStillServed() {
        // Without the mapping scan Spring would refuse v3 as an unsupported version (400).
        runner.run(context -> mvc(context).perform(get("/api/v3/things"))
                .andExpect(status().isOk())
                .andExpect(header().string("API-Version", "3.0.0")));
    }

    @Test
    void errorsUnderTheVersionedApiAreProblemDetails() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/api/v1/pets/404"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/pets/404"))
                    .andExpect(jsonPath("$.message").doesNotExist());

            mvc.perform(post("/api/v1/pets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors.name").exists());

            // A constrained parameter, checked by Spring MVC's built-in method validation.
            mvc.perform(get("/api/v1/pets").param("limit", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors.limit").exists());

            mvc.perform(get("/api/v1/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("hunter2"))));
        });
    }

    @Test
    void springsOwnExceptionsKeepTheirStatus() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            // A path nothing maps was a 500 behind the catch-all handler.
            mvc.perform(get("/api/v1/nothing-here"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("API-Version", "1.2.0"))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            // A major the service does not serve is just a path that does not exist.
            mvc.perform(get("/api/v9/pets").header("API-Version", "1"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().doesNotExist("API-Version"));
            mvc.perform(delete("/api/v1/pets/7"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().exists("Allow"))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
            mvc.perform(post("/api/v1/pets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            mvc.perform(get("/api/v1/pets/abc"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/shipped"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.detail").value("Order already shipped"));
        });
    }

    @Test
    void errorsOutsideTheVersionedApiKeepTheErrorResponseBody() {
        runner.run(context -> mvc(context).perform(get("/legacy/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").doesNotExist()));
    }

    @Test
    void theProblemDetailsScopeIsConfigurable() {
        runner.withPropertyValues("adhar.commons.exception-handler.problem-details=always")
                .run(context -> mvc(context).perform(get("/legacy/missing"))
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.status").value(404)));
        runner.withPropertyValues("adhar.commons.exception-handler.problem-details=never")
                .run(context -> mvc(context).perform(get("/api/v1/pets/404"))
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                        .andExpect(jsonPath("$.status").doesNotExist()));
    }

    @Test
    void anApiVersionAnnotatedEndpointAnnouncesItsOwnDeprecation() {
        runner.run(context -> mvc(context).perform(get("/api/v1/legacy-pets"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "@1788220800"))
                .andExpect(header().string("Sunset", "Sun, 31 Jan 2027 00:00:00 GMT"))
                .andExpect(header().string("X-API-Version", "1"))
                .andExpect(header().string("X-API-Deprecation-Message", "Use GET /api/v2/pets")));

        // When the whole version is deprecated, its announcement wins.
        runner.withPropertyValues("adhar.commons.api-versioning.versions.v1.deprecated=2020-01-01")
                .run(context -> mvc(context).perform(get("/api/v1/legacy-pets"))
                        .andExpect(header().string("Deprecation", "@1577836800")));
    }

    @Test
    void legacyPathsKeepTheApiVersionInterceptorBehaviour() {
        runner.run(context -> mvc(context).perform(get("/legacy/thing"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-API-Version", "1.0"))
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", "Thu, 31 Dec 2026 00:00:00 GMT")));
    }

    @Test
    void versioningCanBeSwitchedOff() {
        runner.withPropertyValues("adhar.commons.api-versioning.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApiVersionRegistry.class);
                    assertThat(context).doesNotHaveBean(ApiVersionHeaderFilter.class);
                    mvc(context).perform(get("/api/v1/pets/7"))
                            .andExpect(status().isOk())
                            .andExpect(header().doesNotExist("API-Version"));
                });
        runner.withPropertyValues("adhar.commons.api-versioning.endpoints.enabled=false")
                .run(context -> mvc(context).perform(get("/api/v1/openapi.yaml"))
                        .andExpect(status().isNotFound()));
    }

    @Test
    void invalidVersionConfigurationFailsStartup() {
        runner.withPropertyValues("adhar.commons.api-versioning.versions.v1.version=2.0.0")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage("API version '2.0.0' does not belong to /api/v1"));
        runner.withPropertyValues("adhar.commons.api-versioning.versions.latest.deprecated=true")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("does not name a major version"));
    }

    private static MockMvc mvc(AssertableWebApplicationContext context) {
        Filter[] filters = context.getBeansOfType(Filter.class).values().stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toArray(Filter[]::new);
        return MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
    }

    record NewPet(@NotBlank String name) {
    }

    @RestController
    static class PetsController {

        @GetMapping("/hello")
        public String hello() {
            return "hi";
        }

        @GetMapping("/api/v1/pets/{id}")
        public Map<String, Object> pet(@PathVariable long id) {
            if (id == 404) {
                throw new ResourceNotFoundException("Pet", id);
            }
            return Map.of("id", id);
        }

        @GetMapping("/api/v1/pets")
        public List<String> pets(@RequestParam(defaultValue = "10") @Min(1) int limit) {
            return List.of();
        }

        @PostMapping("/api/v1/pets")
        public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody NewPet pet) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("name", pet.name()));
        }

        @GetMapping("/api/v2/pets/{id}")
        public Map<String, Object> petV2(@PathVariable String id) {
            return Map.of("id", id);
        }

        @GetMapping("/api/v3/things")
        public List<String> things() {
            return List.of("thing");
        }

        @ApiVersion(version = "1", deprecated = true, deprecationDate = "2026-09-01",
                sunsetDate = "2027-01-31", deprecationMessage = "Use GET /api/v2/pets")
        @GetMapping("/api/v1/legacy-pets")
        public List<String> legacyPets() {
            return List.of();
        }

        @ApiVersion(version = "1.0", deprecated = true, sunsetDate = "2026-12-31")
        @GetMapping("/legacy/thing")
        public String legacyThing() {
            return "old";
        }

        @GetMapping("/api/v1/boom")
        public String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/api/v1/shipped")
        public String shipped() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already shipped");
        }
    }
}
