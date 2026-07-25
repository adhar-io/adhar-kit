package com.adhar.kit.starter.actuator;

import com.adhar.kit.starter.AdharModuleAccess;
import com.adhar.kit.starter.config.AdharKitAutoConfiguration.AdharKitModuleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdharKitEndpointTest {

    private AdharKitModuleRegistry registry(Map<String, Boolean> toggles) {
        return new AdharKitModuleRegistry(AdharModuleAccess.of(toggles), new StaticApplicationContext());
    }

    @Test
    void adhar_reportsEveryModuleWithEnabledCounts() {
        AdharKitEndpoint endpoint = new AdharKitEndpoint(registry(Map.of("ai", false, "metrics", true)));

        AdharKitEndpoint.Report report = endpoint.adhar();

        assertThat(report.version()).isNotBlank();
        assertThat(report.totalModules()).isGreaterThan(0);
        assertThat(report.enabledModules() + report.disabledModules()).isEqualTo(report.totalModules());
        assertThat(report.modules())
                .extracting(AdharKitEndpoint.ModuleStatus::id)
                .contains("ai", "metrics");
        assertThat(report.modules())
                .filteredOn(m -> m.id().equals("ai"))
                .extracting(AdharKitEndpoint.ModuleStatus::status)
                .containsExactly("DISABLED");
        assertThat(report.modules())
                .filteredOn(m -> m.id().equals("metrics"))
                .extracting(AdharKitEndpoint.ModuleStatus::status)
                .containsExactly("UP");
    }

    @Test
    void module_returnsStatusForKnownId() {
        AdharKitEndpoint endpoint = new AdharKitEndpoint(registry(Map.of("ai", false)));

        AdharKitEndpoint.ModuleStatus status = endpoint.module("ai");

        assertThat(status).isNotNull();
        assertThat(status.enabled()).isFalse();
        assertThat(status.status()).isEqualTo("DISABLED");
    }

    @Test
    void module_returnsNullForUnknownId() {
        AdharKitEndpoint endpoint = new AdharKitEndpoint(registry(Map.of()));

        assertThat(endpoint.module("does-not-exist")).isNull();
    }
}
