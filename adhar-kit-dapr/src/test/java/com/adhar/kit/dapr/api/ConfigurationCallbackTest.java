package com.adhar.kit.dapr.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ConfigurationCallback} functional interface.
 */
class ConfigurationCallbackTest {

    @Test
    void callbackReceivesChangedConfiguration() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        ConfigurationCallback callback = captured::set;

        Map<String, String> changes = Map.of("feature.x", "on");
        callback.onConfigurationChange(changes);

        assertThat(captured.get()).isEqualTo(changes);
    }
}
