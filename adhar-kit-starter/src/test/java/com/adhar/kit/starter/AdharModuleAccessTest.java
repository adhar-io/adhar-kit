package com.adhar.kit.starter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdharModuleAccessTest {

    @Test
    void allEnabled_reportsEveryModuleEnabled() {
        assertThat(AdharModuleAccess.ALL_ENABLED.isEnabled("ai")).isTrue();
        assertThat(AdharModuleAccess.ALL_ENABLED.isEnabled("anything-unregistered")).isTrue();
        assertThat(AdharModuleAccess.ALL_ENABLED.disabledModuleIds()).isEmpty();
    }

    @Test
    void of_honorsExplicitToggles() {
        AdharModuleAccess access = AdharModuleAccess.of(Map.of("ai", false, "metrics", true));

        assertThat(access.isEnabled("ai")).isFalse();
        assertThat(access.isEnabled("metrics")).isTrue();
        assertThat(access.disabledModuleIds()).containsExactly("ai");
    }

    @Test
    void of_defaultsUnknownModuleIdsToEnabled() {
        AdharModuleAccess access = AdharModuleAccess.of(Map.of("ai", false));

        assertThat(access.isEnabled("some-future-module")).isTrue();
    }
}
