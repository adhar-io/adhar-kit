package com.adhar.kit.config.manager;

import com.adhar.kit.config.encryption.PropertyEncryptor;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigManagerTest {

    private ConfigManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConfigManager();
    }

    /** Simple controllable ConfigSource for tests. */
    static class TestSource implements ConfigSource {
        private final String type;
        private final int priority;
        private boolean enabled = true;
        private boolean refreshSupported = true;
        private boolean refreshResult = true;
        private boolean healthy = true;
        private final Map<String, Object> data = new HashMap<>();

        TestSource(String type, int priority) {
            this.type = type;
            this.priority = priority;
        }

        TestSource with(String key, Object value) {
            data.put(key, value);
            return this;
        }

        @Override public String getType() { return type; }
        @Override public int getPriority() { return priority; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public Map<String, Object> loadConfig() { return new HashMap<>(data); }
        @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public boolean supportsRefresh() { return refreshSupported; }
        @Override public boolean refresh() { return refreshResult; }
        @Override public boolean isHealthy() { return healthy; }
    }

    @Test
    void addSourceLoadsProperties() {
        manager.addSource(new TestSource("file", 100).with("a", "1"));
        assertThat(manager.getProperty("a")).isEqualTo("1");
        assertThat(manager.containsProperty("a")).isTrue();
    }

    @Test
    void nullSourceIsSkipped() {
        manager.addSource(null);
        assertThat(manager.getProperty("x")).isNull();
    }

    @Test
    void disabledSourceIsSkipped() {
        TestSource s = new TestSource("file", 100).with("a", "1");
        s.enabled = false;
        manager.addSource(s);
        assertThat(manager.containsProperty("a")).isFalse();
    }

    @Test
    void higherPriorityOverridesLowerPriority() {
        manager.addSource(new TestSource("file", 100).with("key", "low"));
        manager.addSource(new TestSource("env", 200).with("key", "high"));
        assertThat(manager.getProperty("key")).isEqualTo("high");
    }

    @Test
    void removeSourceDropsItsProperties() {
        manager.addSource(new TestSource("file", 100).with("a", "1"));
        manager.addSource(new TestSource("env", 200).with("b", "2"));
        manager.removeSource("file");
        assertThat(manager.containsProperty("a")).isFalse();
        assertThat(manager.containsProperty("b")).isTrue();
    }

    @Test
    void getPropertyWithTypeConverts() {
        manager.addSource(new TestSource("file", 100)
                .with("int", "42")
                .with("long", "99")
                .with("bool", "true")
                .with("double", "3.14")
                .with("str", "hello"));

        assertThat(manager.getProperty("int", Integer.class)).isEqualTo(42);
        assertThat(manager.getProperty("long", Long.class)).isEqualTo(99L);
        assertThat(manager.getProperty("bool", Boolean.class)).isTrue();
        assertThat(manager.getProperty("double", Double.class)).isEqualTo(3.14);
        assertThat(manager.getProperty("str", String.class)).isEqualTo("hello");
    }

    @Test
    void getPropertyWithTypeReturnsNullWhenAbsent() {
        assertThat(manager.getProperty("missing", String.class)).isNull();
    }

    @Test
    void getPropertyReturnsSameInstanceWhenAlreadyTargetType() {
        manager.addSource(new TestSource("file", 100).with("num", 7));
        assertThat(manager.getProperty("num", Integer.class)).isEqualTo(7);
    }

    @Test
    void getPropertyWithDefaultUsesDefaultWhenAbsent() {
        manager.addSource(new TestSource("file", 100).with("present", "v"));
        assertThat(manager.getProperty("present", String.class, "def")).isEqualTo("v");
        assertThat(manager.getProperty("missing", String.class, "def")).isEqualTo("def");
    }

    @Test
    void getRequiredPropertyReturnsValueOrThrows() {
        manager.addSource(new TestSource("file", 100).with("present", "v"));
        assertThat(manager.getRequiredProperty("present", String.class)).isEqualTo("v");
        assertThatThrownBy(() -> manager.getRequiredProperty("missing", String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getPropertiesWithPrefixFilters() {
        manager.addSource(new TestSource("file", 100)
                .with("db.url", "u")
                .with("db.user", "admin")
                .with("other", "x"));
        Map<String, Object> dbProps = manager.getPropertiesWithPrefix("db.");
        assertThat(dbProps).containsKeys("db.url", "db.user").doesNotContainKey("other");
    }

    @Test
    void refreshAllNotifiesListenersOfChanges() {
        TestSource source = new TestSource("file", 100).with("key", "v1");
        manager.addSource(source);

        AtomicReference<Object> newVal = new AtomicReference<>();
        manager.addChangeListener((key, oldValue, newValue) -> {
            if ("key".equals(key)) {
                newVal.set(newValue);
            }
        });

        source.data.put("key", "v2");
        manager.refreshAll();

        assertThat(manager.getProperty("key")).isEqualTo("v2");
        assertThat(newVal.get()).isEqualTo("v2");
    }

    @Test
    void refreshAllSkipsSourcesNotSupportingRefresh() {
        TestSource source = new TestSource("file", 100).with("a", "1");
        source.refreshSupported = false;
        manager.addSource(source);
        manager.refreshAll();
        assertThat(manager.getProperty("a")).isEqualTo("1");
    }

    @Test
    void refreshSourceRefreshesNamedSource() {
        TestSource source = new TestSource("file", 100).with("key", "v1");
        manager.addSource(source);
        source.data.put("key", "v2");
        manager.refreshSource("file");
        assertThat(manager.getProperty("key")).isEqualTo("v2");
    }

    @Test
    void refreshSourceWhenResultFalseDoesNotReload() {
        TestSource source = new TestSource("file", 100).with("key", "v1");
        source.refreshResult = false;
        manager.addSource(source);
        source.data.put("key", "v2");
        manager.refreshSource("file");
        assertThat(manager.getProperty("key")).isEqualTo("v1");
    }

    @Test
    void removeChangeListenerStopsNotifications() {
        TestSource source = new TestSource("file", 100).with("key", "v1");
        manager.addSource(source);
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        ConfigManager.ConfigChangeListener listener = (k, o, n) -> called.set(true);
        manager.addChangeListener(listener);
        manager.removeChangeListener(listener);

        source.data.put("key", "v2");
        manager.refreshAll();
        assertThat(called.get()).isFalse();
    }

    @Test
    void listenerExceptionDoesNotBreakRefresh() {
        TestSource source = new TestSource("file", 100).with("key", "v1");
        manager.addSource(source);
        manager.addChangeListener((k, o, n) -> { throw new RuntimeException("boom"); });
        source.data.put("key", "v2");
        manager.refreshAll();
        assertThat(manager.getProperty("key")).isEqualTo("v2");
    }

    @Test
    void getPropertyDecryptsEncValuesWhenEncryptorSet() {
        PropertyEncryptor encryptor = new PropertyEncryptor("unit-test-key", "AES", "unit-salt", 10_000);
        String encrypted = encryptor.encrypt("s3cr3t");

        manager.setEncryptor(encryptor);
        manager.addSource(new TestSource("file", 100)
                .with("database.password", encrypted)
                .with("database.username", "admin"));

        assertThat(manager.getProperty("database.password")).isEqualTo("s3cr3t");
        assertThat(manager.getProperty("database.password", String.class)).isEqualTo("s3cr3t");
        assertThat(manager.getRequiredProperty("database.password", String.class)).isEqualTo("s3cr3t");
        // Non-encrypted values pass through untouched
        assertThat(manager.getProperty("database.username")).isEqualTo("admin");
    }

    @Test
    void getPropertiesWithPrefixDecryptsEncValues() {
        PropertyEncryptor encryptor = new PropertyEncryptor("unit-test-key", "AES", "unit-salt", 10_000);
        manager.setEncryptor(encryptor);
        manager.addSource(new TestSource("file", 100)
                .with("db.password", encryptor.encrypt("hidden"))
                .with("db.url", "jdbc:x"));

        Map<String, Object> props = manager.getPropertiesWithPrefix("db.");
        assertThat(props).containsEntry("db.password", "hidden").containsEntry("db.url", "jdbc:x");
    }

    @Test
    void withoutEncryptorEncValuesAreReturnedRaw() {
        PropertyEncryptor encryptor = new PropertyEncryptor("unit-test-key", "AES", "unit-salt", 10_000);
        String encrypted = encryptor.encrypt("s3cr3t");
        manager.addSource(new TestSource("file", 100).with("database.password", encrypted));

        assertThat(manager.getProperty("database.password")).isEqualTo(encrypted);
    }

    @Test
    void nonStringValuesBypassDecryption() {
        PropertyEncryptor encryptor = new PropertyEncryptor("unit-test-key", "AES", "unit-salt", 10_000);
        manager.setEncryptor(encryptor);
        manager.addSource(new TestSource("file", 100).with("port", 8080));

        assertThat(manager.getProperty("port")).isEqualTo(8080);
    }

    @Test
    void setEncryptorNullDisablesDecryption() {
        PropertyEncryptor encryptor = new PropertyEncryptor("unit-test-key", "AES", "unit-salt", 10_000);
        String encrypted = encryptor.encrypt("s3cr3t");
        manager.addSource(new TestSource("file", 100).with("k", encrypted));

        manager.setEncryptor(encryptor);
        assertThat(manager.getProperty("k")).isEqualTo("s3cr3t");

        manager.setEncryptor(null);
        assertThat(manager.getProperty("k")).isEqualTo(encrypted);
    }

    @Test
    void getHealthStatusReportsPerSource() {
        TestSource healthy = new TestSource("file", 100);
        TestSource unhealthy = new TestSource("env", 200);
        unhealthy.healthy = false;
        manager.addSource(healthy);
        manager.addSource(unhealthy);

        Map<String, Boolean> status = manager.getHealthStatus();
        assertThat(status).containsEntry("file", true).containsEntry("env", false);
    }
}
