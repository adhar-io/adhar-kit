package com.adhar.kit.config.source.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentConfigSourceTest {

    @Test
    void typeAndPriorityDefaults() {
        EnvironmentConfigSource source = new EnvironmentConfigSource();
        assertThat(source.getType()).isEqualTo("environment");
        assertThat(source.getPriority()).isEqualTo(200);
        assertThat(source.supportsRefresh()).isTrue();
    }

    @Test
    void customPriorityConstructor() {
        EnvironmentConfigSource source = new EnvironmentConfigSource("APP_", 350);
        assertThat(source.getPriority()).isEqualTo(350);
    }

    @Test
    void nullPrefixTreatedAsEmpty() {
        EnvironmentConfigSource source = new EnvironmentConfigSource(null, 100);
        assertThat(source.loadConfig()).isNotNull();
    }

    @Test
    void loadConfigContainsTransformedEnvKeys() {
        EnvironmentConfigSource source = new EnvironmentConfigSource();
        Map<String, Object> config = source.loadConfig();
        assertThat(config).isNotEmpty();
        // All keys should be lowercase with dots (no uppercase, no underscores)
        config.keySet().forEach(key -> {
            assertThat(key).isEqualTo(key.toLowerCase());
            assertThat(key).doesNotContain("_");
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PATH", matches = ".+")
    void getPropertyReadsKnownEnvVar() {
        EnvironmentConfigSource source = new EnvironmentConfigSource();
        assertThat(source.getProperty("path")).isPresent();
    }

    @Test
    void getPropertyAbsentReturnsEmpty() {
        EnvironmentConfigSource source = new EnvironmentConfigSource("ZZ_NONEXISTENT_PREFIX_", 100);
        assertThat(source.getProperty("anything")).isEmpty();
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void refreshReloadsAndReturnsTrue() {
        EnvironmentConfigSource source = new EnvironmentConfigSource();
        assertThat(source.refresh()).isTrue();
        assertThat(source.loadConfig()).isNotEmpty();
    }

    @Test
    void configKeyToEnvKeyConverts() {
        assertThat(EnvironmentConfigSource.configKeyToEnvKey("database.url")).isEqualTo("DATABASE_URL");
        assertThat(EnvironmentConfigSource.configKeyToEnvKey("app.server.port")).isEqualTo("APP_SERVER_PORT");
    }
}
