package com.adhar.kit.config.source;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSourceTest {

    /** Minimal implementation relying on interface default methods. */
    static class MinimalSource implements ConfigSource {
        private final boolean throwOnLoad;
        MinimalSource(boolean throwOnLoad) { this.throwOnLoad = throwOnLoad; }

        @Override public String getType() { return "minimal"; }
        @Override public Map<String, Object> loadConfig() {
            if (throwOnLoad) {
                throw new IllegalStateException("cannot load");
            }
            Map<String, Object> m = new HashMap<>();
            m.put("k", "v");
            return m;
        }
        @Override public Optional<Object> getProperty(String key) {
            return Optional.ofNullable(loadConfig().get(key));
        }
        @Override public boolean supportsRefresh() { return false; }
    }

    @Test
    void defaultPriorityIs100() {
        assertThat(new MinimalSource(false).getPriority()).isEqualTo(100);
    }

    @Test
    void defaultIsEnabledIsTrue() {
        assertThat(new MinimalSource(false).isEnabled()).isTrue();
    }

    @Test
    void defaultRefreshReturnsFalse() {
        assertThat(new MinimalSource(false).refresh()).isFalse();
    }

    @Test
    void defaultIsHealthyTrueWhenLoadSucceeds() {
        assertThat(new MinimalSource(false).isHealthy()).isTrue();
    }

    @Test
    void defaultIsHealthyFalseWhenLoadThrows() {
        assertThat(new MinimalSource(true).isHealthy()).isFalse();
    }
}
