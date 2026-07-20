package com.adhar.kit.config;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFacadeTest {

    private ConfigFacade facade;
    private Map<String, String> localProps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        facade = ConfigFacade.getInstance();
        facade.setConfigManager(null);
        Field propsField = ConfigFacade.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        localProps = (Map<String, String>) propsField.get(facade);
        localProps.clear();
        localProps.put("payment.gateway.url", "https://gw.example.com");
        localProps.put("payment.max-retries", "5");
        localProps.put("http.timeout.ms", "1500");
        localProps.put("features.enabled", "true");
        localProps.put("ratio", "0.75");
        localProps.put("not.a.number", "abc");
        localProps.put("spring.profiles.active", "prod");
        localProps.put("database.url", "jdbc:postgresql://db");
        localProps.put("database.username", "admin");
    }

    @AfterEach
    void tearDown() {
        facade.setConfigManager(null);
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
    void refreshInStandaloneModeDoesNotThrow() {
        facade.refresh();
    }

    @Test
    void isRefreshableFalseInStandaloneMode() {
        assertThat(facade.isRefreshable()).isFalse();
    }

    /** Simple controllable ConfigSource for delegation tests. */
    static class TestSource implements ConfigSource {
        final Map<String, Object> data = new HashMap<>();

        @Override public String getType() { return "test"; }
        @Override public Map<String, Object> loadConfig() { return new HashMap<>(data); }
        @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public boolean supportsRefresh() { return true; }
        @Override public boolean refresh() { return true; }
    }

    @Nested
    class DelegationMode {

        private ConfigManager manager;
        private TestSource source;

        @BeforeEach
        void linkManager() {
            manager = new ConfigManager();
            source = new TestSource();
            source.data.put("app.name", "adhar");
            source.data.put("app.port", "8080");
            source.data.put("app.debug", "true");
            source.data.put("app.rate", "2.5");
            manager.addSource(source);
            facade.setConfigManager(manager);
        }

        @Test
        void getDelegatesToManager() {
            assertThat(facade.get("app.name")).isEqualTo("adhar");
        }

        @Test
        void typedGettersDelegateToManager() {
            assertThat(facade.getInt("app.port", 1)).isEqualTo(8080);
            assertThat(facade.getLong("app.port", 1L)).isEqualTo(8080L);
            assertThat(facade.getBoolean("app.debug", false)).isTrue();
            assertThat(facade.getDouble("app.rate", 0.0)).isEqualTo(2.5);
        }

        @Test
        void getOptionalDelegatesToManager() {
            assertThat(facade.getOptional("app.name")).contains("adhar");
            assertThat(facade.getOptional("app.missing")).isEmpty();
        }

        @Test
        void localMapActsAsFallback() {
            // present only in the local map, not in the manager
            assertThat(facade.get("payment.gateway.url")).isEqualTo("https://gw.example.com");
        }

        @Test
        void managerValueWinsOverLocalMap() {
            localProps.put("app.name", "local-value");
            assertThat(facade.get("app.name")).isEqualTo("adhar");
        }

        @Test
        void containsKeyChecksManagerAndLocalMap() {
            assertThat(facade.containsKey("app.name")).isTrue();
            assertThat(facade.containsKey("payment.gateway.url")).isTrue();
            assertThat(facade.containsKey("nowhere")).isFalse();
        }

        @Test
        void getPropertiesWithPrefixMergesManagerAndLocal() {
            localProps.put("app.local-only", "x");
            Map<String, String> props = facade.getPropertiesWithPrefix("app");
            assertThat(props)
                    .containsEntry("name", "adhar")
                    .containsEntry("port", "8080")
                    .containsEntry("local-only", "x");
        }

        @Test
        void refreshDelegatesToManagerRefreshAll() {
            source.data.put("app.name", "refreshed");
            facade.refresh();
            assertThat(facade.get("app.name")).isEqualTo("refreshed");
        }

        @Test
        void isRefreshableTrueWhenManagerLinked() {
            assertThat(facade.isRefreshable()).isTrue();
        }
    }
}
