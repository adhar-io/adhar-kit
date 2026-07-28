package com.adhar.kit.config.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMaskingTest {

    @Test
    void detectsSecretKeysWithDefaults() {
        assertThat(ConfigMasking.isSecretKey("db.password")).isTrue();
        assertThat(ConfigMasking.isSecretKey("api.token")).isTrue();
        assertThat(ConfigMasking.isSecretKey("service.apiKey")).isTrue();
        assertThat(ConfigMasking.isSecretKey("some.credential")).isTrue();
        assertThat(ConfigMasking.isSecretKey("database.url")).isFalse();
        assertThat(ConfigMasking.isSecretKey(null)).isFalse();
    }

    @Test
    void masksSecretValuesOnly() {
        assertThat(ConfigMasking.maskIfSecret("db.password", "hunter2")).isEqualTo("***");
        assertThat(ConfigMasking.maskIfSecret("db.url", "jdbc:pg")).isEqualTo("jdbc:pg");
        assertThat(ConfigMasking.maskIfSecret("db.password", null)).isNull();
        assertThat(ConfigMasking.mask()).isEqualTo("***");
    }

    @Test
    void customPatternsRespected() {
        List<String> patterns = List.of("ssn");
        assertThat(ConfigMasking.isSecretKey("user.ssn", patterns)).isTrue();
        assertThat(ConfigMasking.isSecretKey("user.password", patterns)).isFalse();
        assertThat(ConfigMasking.maskIfSecret("user.ssn", "123", patterns)).isEqualTo("***");
        assertThat(ConfigMasking.isSecretKey("x", null)).isFalse();
    }
}
