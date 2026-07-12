package com.adhar.kit.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFacadeTest {

    private ConfigFacade facade;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        facade = ConfigFacade.getInstance();
        Field propsField = ConfigFacade.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        Map<String, String> props = (Map<String, String>) propsField.get(facade);
        props.clear();
        props.put("payment.gateway.url", "https://gw.example.com");
        props.put("payment.max-retries", "5");
        props.put("http.timeout.ms", "1500");
        props.put("features.enabled", "true");
        props.put("ratio", "0.75");
        props.put("not.a.number", "abc");
        props.put("spring.profiles.active", "prod");
        props.put("database.url", "jdbc:postgresql://db");
        props.put("database.username", "admin");
    }

    @Test
    void getInstanceReturnsSingleton() {
        assertThat(ConfigFacade.getInstance()).isSameAs(facade);
    }

    @Test
    void getExistingKeyReturnsValue() {
        assertThat(facade.get("payment.gateway.url")).isEqualTo("https://gw.example.com");
    }

    @Test
    void getMissingKeyThrows() {
        assertThatThrownBy(() -> facade.get("missing.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.key");
    }

    @Test
    void getWithDefaultReturnsValueOrDefault() {
        assertThat(facade.get("payment.gateway.url", "fallback")).isEqualTo("https://gw.example.com");
        assertThat(facade.get("missing.key", "fallback")).isEqualTo("fallback");
    }

    @Test
    void getOptionalReturnsPresentOrEmpty() {
        assertThat(facade.getOptional("payment.gateway.url")).contains("https://gw.example.com");
        assertThat(facade.getOptional("missing.key")).isEmpty();
        assertThat(facade.getOptional("missing.key")).isInstanceOf(Optional.class);
    }

    @Test
    void getIntParsesOrUsesDefault() {
        assertThat(facade.getInt("payment.max-retries", 3)).isEqualTo(5);
        assertThat(facade.getInt("missing", 3)).isEqualTo(3);
        assertThat(facade.getInt("not.a.number", 7)).isEqualTo(7);
    }

    @Test
    void getLongParsesOrUsesDefault() {
        assertThat(facade.getLong("http.timeout.ms", 1000L)).isEqualTo(1500L);
        assertThat(facade.getLong("missing", 1000L)).isEqualTo(1000L);
        assertThat(facade.getLong("not.a.number", 99L)).isEqualTo(99L);
    }

    @Test
    void getBooleanParsesOrUsesDefault() {
        assertThat(facade.getBoolean("features.enabled", false)).isTrue();
        assertThat(facade.getBoolean("missing", true)).isTrue();
    }

    @Test
    void getDoubleParsesOrUsesDefault() {
        assertThat(facade.getDouble("ratio", 1.0)).isEqualTo(0.75);
        assertThat(facade.getDouble("missing", 2.5)).isEqualTo(2.5);
        assertThat(facade.getDouble("not.a.number", 4.2)).isEqualTo(4.2);
    }

    @Test
    void getPropertiesWithPrefixStripsPrefix() {
        Map<String, String> dbProps = facade.getPropertiesWithPrefix("database");
        assertThat(dbProps).containsEntry("url", "jdbc:postgresql://db")
                .containsEntry("username", "admin")
                .doesNotContainKey("payment.gateway.url");
    }

    @Test
    void getPropertiesWithPrefixHandlesTrailingDot() {
        Map<String, String> dbProps = facade.getPropertiesWithPrefix("database.");
        assertThat(dbProps).containsEntry("url", "jdbc:postgresql://db");
    }

    @Test
    void containsKeyReflectsPresence() {
        assertThat(facade.containsKey("database.url")).isTrue();
        assertThat(facade.containsKey("missing")).isFalse();
    }

    @Test
    void getActiveProfileReturnsConfiguredValue() {
        assertThat(facade.getActiveProfile()).isEqualTo("prod");
    }

    @Test
    void refreshDoesNotThrow() {
        facade.refresh();
    }

    @Test
    void isRefreshableReturnsTrue() {
        assertThat(facade.isRefreshable()).isTrue();
    }
}
