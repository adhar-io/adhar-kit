package com.adhar.kit.config.endpoint;

import com.adhar.kit.config.audit.ConfigChangeAuditPublisher;
import com.adhar.kit.config.featureflag.FeatureFlag;
import com.adhar.kit.config.featureflag.FeatureFlagService;
import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdharConfigEndpointTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
        };
    }

    private ConfigManager managerWith(Map<String, Object> props) {
        ConfigManager manager = new ConfigManager();
        manager.addSource(new ConfigSource() {
            @Override public String getType() { return "test"; }
            @Override public Map<String, Object> loadConfig() { return new HashMap<>(props); }
            @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(props.get(key)); }
            @Override public boolean supportsRefresh() { return false; }
        });
        return manager;
    }

    @Test
    void reportsSourcesMaskedPropertiesFlagsAndChanges() {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "8080");
        props.put("db.password", "s3cr3t");
        ConfigManager manager = managerWith(props);

        FeatureFlagService flags = new FeatureFlagService();
        flags.setFlag(new FeatureFlag("new-ui", true, 25, Set.of("vip"), Set.of("bad")));

        ConfigChangeAuditPublisher audit = new ConfigChangeAuditPublisher(e -> {}, List.of(), 10);
        audit.onConfigChange("server.port", "8080", "9090");

        AdharConfigEndpoint endpoint = new AdharConfigEndpoint(manager, provider(flags), provider(audit));
        Map<String, Object> result = endpoint.config();

        assertThat(result).containsKeys("sources", "properties", "featureFlags", "recentChanges");

        List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");
        assertThat(sources).anySatisfy(s -> assertThat(s.get("type")).isEqualTo("test"));

        Map<String, Object> masked = (Map<String, Object>) result.get("properties");
        assertThat(masked.get("server.port")).isEqualTo("8080");
        assertThat(masked.get("db.password")).isEqualTo("***");

        Map<String, Object> flagView = (Map<String, Object>) result.get("featureFlags");
        Map<String, Object> newUi = (Map<String, Object>) flagView.get("new-ui");
        assertThat(newUi.get("rolloutPercentage")).isEqualTo(25);
        assertThat(newUi.get("allowList")).isEqualTo(Set.of("vip"));

        List<Map<String, Object>> changes = (List<Map<String, Object>>) result.get("recentChanges");
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).get("key")).isEqualTo("server.port");
    }

    @Test
    void worksWithoutOptionalCollaborators() {
        AdharConfigEndpoint endpoint = new AdharConfigEndpoint(
                managerWith(Map.of("a", "b")), provider(null), provider(null));
        Map<String, Object> result = endpoint.config();
        assertThat((Map<String, Object>) result.get("featureFlags")).isEmpty();
        assertThat((List<?>) result.get("recentChanges")).isEmpty();
        assertThat((Map<String, Object>) result.get("properties")).containsEntry("a", "b");
    }
}
