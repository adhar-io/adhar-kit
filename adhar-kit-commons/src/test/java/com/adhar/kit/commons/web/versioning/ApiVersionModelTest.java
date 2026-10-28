package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.config.AdharCommonsProperties;
import jakarta.servlet.http.MappingMatch;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletMapping;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the versioning model: paths, requested versions, dates, a major version and
 * the registry built from configuration and shipped specs.
 */
class ApiVersionModelTest {

    @Test
    void theMajorIsTheSecondPathSegment() {
        assertThat(ApiPaths.majorOf("/api/v1/pets")).hasValue(1);
        assertThat(ApiPaths.majorOf("/api/v12")).hasValue(12);
        assertThat(ApiPaths.majorOf("/api/v0/x")).hasValue(0);
        assertThat(ApiPaths.majorOf("/api/v1x/pets")).isEmpty();
        assertThat(ApiPaths.majorOf("/api/v01/pets")).isEmpty();
        assertThat(ApiPaths.majorOf("/api")).isEmpty();
        assertThat(ApiPaths.majorOf("/actuator/health")).isEmpty();
        assertThat(ApiPaths.majorOf("/v1/pets")).isEmpty();
        assertThat(ApiPaths.majorOf("/api/v99999999999/pets")).isEmpty();
        assertThat(ApiPaths.majorOf(null)).isEmpty();
    }

    @Test
    void requestedVersionsNameTheirMajor() {
        assertThat(ApiPaths.majorOfRequestedVersion("1")).hasValue(1);
        assertThat(ApiPaths.majorOfRequestedVersion("v2")).hasValue(2);
        assertThat(ApiPaths.majorOfRequestedVersion("V3")).hasValue(3);
        assertThat(ApiPaths.majorOfRequestedVersion("1.4")).hasValue(1);
        assertThat(ApiPaths.majorOfRequestedVersion(" 1.4.2 ")).hasValue(1);
        assertThat(ApiPaths.majorOfRequestedVersion("2.0.0-rc.1")).hasValue(2);
        assertThat(ApiPaths.majorOfRequestedVersion("01")).isEqualTo(OptionalInt.empty());
        assertThat(ApiPaths.majorOfRequestedVersion("1.2.3.4")).isEmpty();
        assertThat(ApiPaths.majorOfRequestedVersion("latest")).isEmpty();
        assertThat(ApiPaths.majorOfRequestedVersion(null)).isEmpty();
    }

    @Test
    void thePathWithinTheApplicationDropsContextAndServletPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/api/v1/orders");
        request.setContextPath("/shop");
        assertThat(ApiPaths.pathWithinApplication(request)).isEqualTo("/api/v1/orders");

        MockHttpServletRequest prefixed = new MockHttpServletRequest("GET", "/shop/app/api/v2/orders");
        prefixed.setContextPath("/shop");
        prefixed.setServletPath("/app");
        prefixed.setHttpServletMapping(new MockHttpServletMapping("api/v2/orders", "/app/*", "dispatcher",
                MappingMatch.PATH));
        assertThat(ApiPaths.pathWithinApplication(prefixed)).isEqualTo("/api/v2/orders");

        assertThat(ApiPaths.pathWithinApplication(new MockHttpServletRequest("GET", ""))).isEqualTo("/");
    }

    @Test
    void datesAcceptTheFormatsConfigurationUses() {
        assertThat(ApiVersionDates.instant("2026-09-01", "p")).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(ApiVersionDates.instant("2026-09-01T12:00:00+02:00", "p"))
                .isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(ApiVersionDates.instant("2026-09-01T10:00:00", "p")).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(ApiVersionDates.instant("1788220800", "p")).isEqualTo(Instant.ofEpochSecond(1788220800));
        assertThat(ApiVersionDates.instant(" ", "p")).isNull();
        assertThatThrownBy(() -> ApiVersionDates.instant("next year", "x.sunset"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x.sunset 'next year' is not a date");

        assertThat(ApiVersionDates.deprecation("true", "p")).isEqualTo(Instant.EPOCH);
        assertThat(ApiVersionDates.deprecation("FALSE", "p")).isNull();
        assertThat(ApiVersionDates.deprecation(null, "p")).isNull();
        assertThat(ApiVersionDates.deprecation("2026-09-01", "p")).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void aMajorVersionFormatsItsHeaders() {
        ApiMajorVersion version = new ApiMajorVersion(1, "1.9.0", Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2027-01-31T23:59:59Z"), "/api/v2", URI.create("https://docs/v1"), null, null);

        assertThat(version.prefix()).isEqualTo("/api/v1");
        assertThat(version.specPath()).isEqualTo("/api/v1/openapi.yaml");
        assertThat(version.deprecationHeader()).isEqualTo("@1788220800");
        assertThat(version.sunsetHeader()).isEqualTo("Sun, 31 Jan 2027 23:59:59 GMT");
        assertThat(version.successorLinkHeader()).isEqualTo("</api/v2>; rel=\"successor-version\"");
        assertThat(version.status(Instant.parse("2026-08-31T23:59:59Z"))).isEqualTo(ApiMajorVersion.STATUS_CURRENT);
        assertThat(version.status(Instant.parse("2026-09-01T00:00:00Z"))).isEqualTo(ApiMajorVersion.STATUS_DEPRECATED);

        ApiMajorVersion current = ApiMajorVersion.of(2, "2.0.0");
        assertThat(current.deprecationAnnounced()).isFalse();
        assertThat(current.deprecationHeader()).isNull();
        assertThat(current.sunsetHeader()).isNull();
        assertThat(current.successorLinkHeader()).isNull();
    }

    @Test
    void aMajorVersionRefusesVersionsThatAreNotItsOwn() {
        assertThatThrownBy(() -> ApiMajorVersion.of(1, "1.0"))
                .hasMessageContaining("is not a semantic version");
        assertThatThrownBy(() -> ApiMajorVersion.of(1, "2.0.0"))
                .hasMessage("API version '2.0.0' does not belong to /api/v1");
        assertThatThrownBy(() -> ApiMajorVersion.of(1, "10.0.0")).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> ApiMajorVersion.of(-1, "1.0.0")).hasMessageContaining(">= 0");
        assertThat(ApiMajorVersion.of(1, "1.0.0-rc.1+build.5").version()).isEqualTo("1.0.0-rc.1+build.5");
    }

    @Test
    void theRegistryCombinesShippedSpecsAndConfiguration() {
        AdharCommonsProperties.ApiVersioning properties = new AdharCommonsProperties.ApiVersioning();
        properties.setSpecLocations(List.of("classpath*:versioning/api/v*/openapi.yaml"));
        AdharCommonsProperties.ApiVersioning.Version v1 = new AdharCommonsProperties.ApiVersioning.Version();
        v1.setDeprecated("2026-09-01");
        v1.setSunset("2027-01-31");
        properties.getVersions().put("v1", v1);
        AdharCommonsProperties.ApiVersioning.Version v4 = new AdharCommonsProperties.ApiVersioning.Version();
        v4.setVersion("4.1.0");
        properties.getVersions().put("4", v4);

        ApiVersionRegistry registry = ApiVersionRegistry.load(properties, new PathMatchingResourcePatternResolver());

        assertThat(registry.versions()).extracting(ApiMajorVersion::major).containsExactly(1, 2, 4);
        ApiMajorVersion first = registry.get(1).orElseThrow();
        assertThat(first.version()).isEqualTo("1.2.0");           // info.version of the shipped spec
        assertThat(first.spec()).isNotNull();
        assertThat(first.successor()).isEqualTo("/api/v2");      // the next major, by default
        assertThat(registry.get(2).orElseThrow().successor()).isNull();
        assertThat(registry.get(4).orElseThrow().spec()).isNull();
        assertThat(registry.forPath("/api/v2/pets")).map(ApiMajorVersion::version).hasValue("2.0.1");
        assertThat(registry.forPath("/api/v3/pets")).isEmpty();
        assertThat(registry.forPath("/health")).isEmpty();

        assertThat(registry.registerMapped(3)).isTrue();
        assertThat(registry.registerMapped(3)).isFalse();
        assertThat(registry.registerMapped(1)).isFalse();
        assertThat(registry.get(3)).map(ApiMajorVersion::version).hasValue("3.0.0");
    }

    @Test
    void theRegistryRejectsContradictoryConfiguration() {
        AdharCommonsProperties.ApiVersioning properties = new AdharCommonsProperties.ApiVersioning();
        properties.setSpecLocations(List.of());
        AdharCommonsProperties.ApiVersioning.Version v1 = new AdharCommonsProperties.ApiVersioning.Version();
        v1.setDeprecated("2027-01-01");
        v1.setSunset("2026-01-01");
        properties.getVersions().put("v1", v1);
        assertThatThrownBy(() -> ApiVersionRegistry.load(properties, new PathMatchingResourcePatternResolver()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sunset is before its deprecation");

        v1.setSunset("whenever");
        assertThatThrownBy(() -> ApiVersionRegistry.load(properties, new PathMatchingResourcePatternResolver()))
                .hasMessageContaining("'whenever' is not a date");

        assertThatThrownBy(() -> new ApiVersionRegistry(List.of(ApiMajorVersion.of(1, "1.0.0"),
                ApiMajorVersion.of(1, "1.1.0")))).hasMessageContaining("declared twice");
    }
}
