package com.adhar.kit.config.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangeEventTest {

    @Test
    void masksSecretValues() {
        ConfigChangeEvent event = new ConfigChangeEvent(this, "db.password", "old", "new", "vault");
        assertThat(event.isSecret()).isTrue();
        assertThat(event.getOldValue()).isEqualTo("***");
        assertThat(event.getNewValue()).isEqualTo("***");
        assertThat(event.getKey()).isEqualTo("db.password");
        assertThat(event.getSourceType()).isEqualTo("vault");
        assertThat(event.getChangedAt()).isNotNull();
        assertThat(event.toString()).contains("db.password");
    }

    @Test
    void keepsNonSecretValues() {
        ConfigChangeEvent event = new ConfigChangeEvent(this, "server.port", 8080, 9090, null);
        assertThat(event.isSecret()).isFalse();
        assertThat(event.getOldValue()).isEqualTo(8080);
        assertThat(event.getNewValue()).isEqualTo(9090);
    }
}
