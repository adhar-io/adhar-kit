package com.adhar.kit.config.audit;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangeAuditPublisherTest {

    @Test
    void publishesEventAndRecordsHistory() {
        AtomicReference<Object> published = new AtomicReference<>();
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(
                published::set, List.of(), 10);

        publisher.onConfigChange("server.port", "8080", "9090");

        assertThat(published.get()).isInstanceOf(ConfigChangeEvent.class);
        ConfigChangeEvent event = (ConfigChangeEvent) published.get();
        assertThat(event.getKey()).isEqualTo("server.port");
        assertThat(event.getNewValue()).isEqualTo("9090");

        List<ConfigChangeEvent> recent = publisher.getRecentEvents();
        assertThat(recent).hasSize(1);
    }

    @Test
    void secretValuesMaskedInAudit() {
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(e -> {}, null, 10);
        publisher.onConfigChange("db.password", "old", "new");
        assertThat(publisher.getRecentEvents().get(0).getNewValue()).isEqualTo("***");
    }

    @Test
    void ringBufferHonoursMaxEventsNewestFirst() {
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(e -> {}, List.of(), 3);
        for (int i = 0; i < 5; i++) {
            publisher.onConfigChange("k" + i, null, "v" + i);
        }
        List<ConfigChangeEvent> recent = publisher.getRecentEvents();
        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).getKey()).isEqualTo("k4"); // newest first
        assertThat(recent.get(2).getKey()).isEqualTo("k2");

        publisher.clear();
        assertThat(publisher.getRecentEvents()).isEmpty();
    }

    @Test
    void nullEventPublisherToleratedAndMaxEventsFloored() {
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(null, List.of(), 0);
        publisher.onConfigChange("a", null, "b");
        assertThat(publisher.getRecentEvents()).hasSize(1);
    }

    @Test
    void integratesWithConfigManagerRefresh() {
        ConfigManager manager = new ConfigManager();
        AtomicReference<Object> published = new AtomicReference<>();
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(published::set, List.of(), 10);
        manager.addChangeListener(publisher);

        MutableSource source = new MutableSource();
        manager.addSource(source); // populates cache silently (no change event on add)

        manager.refreshAll(); // source flips value -> change event fired

        assertThat(published.get()).isInstanceOf(ConfigChangeEvent.class);
        assertThat(publisher.getRecentEvents().get(0).getKey()).isEqualTo("feature.x");
    }

    /** Source whose single property value flips when refreshed. */
    static class MutableSource implements ConfigSource {
        private final Map<String, Object> data = new HashMap<>(Map.of("feature.x", "on"));

        @Override public String getType() { return "mutable"; }
        @Override public Map<String, Object> loadConfig() { return new HashMap<>(data); }
        @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public boolean supportsRefresh() { return true; }
        @Override public boolean refresh() { data.put("feature.x", "off"); return true; }
    }
}
