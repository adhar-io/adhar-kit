package com.adhar.kit.config.audit;

import com.adhar.kit.config.manager.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bridges {@link ConfigManager} property changes to Spring {@link ConfigChangeEvent}s
 * and retains a bounded, in-memory audit trail of recent changes.
 *
 * <p>Registered as a {@link ConfigManager.ConfigChangeListener}, it publishes one
 * {@link ConfigChangeEvent} per changed key (values masked for secret-looking
 * keys) and records the event so the actuator endpoint can surface recent
 * configuration changes.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConfigChangeAuditPublisher implements ConfigManager.ConfigChangeListener {

    private final ApplicationEventPublisher eventPublisher;
    private final List<String> secretPatterns;
    private final int maxEvents;
    private final Deque<ConfigChangeEvent> recentEvents = new ArrayDeque<>();

    /**
     * Creates a publisher.
     *
     * @param eventPublisher Spring event publisher
     * @param secretPatterns case-insensitive substrings marking a key as secret
     * @param maxEvents maximum number of recent events retained (&gt;= 1)
     */
    public ConfigChangeAuditPublisher(ApplicationEventPublisher eventPublisher,
                                      List<String> secretPatterns,
                                      int maxEvents) {
        this.eventPublisher = eventPublisher;
        this.secretPatterns = secretPatterns == null || secretPatterns.isEmpty()
                ? ConfigMasking.DEFAULT_SECRET_PATTERNS : secretPatterns;
        this.maxEvents = Math.max(1, maxEvents);
    }

    @Override
    public void onConfigChange(String key, Object oldValue, Object newValue) {
        ConfigChangeEvent event = new ConfigChangeEvent(this, key, oldValue, newValue, null, secretPatterns);
        record(event);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
        log.debug("Config change audited: {}", event);
    }

    private synchronized void record(ConfigChangeEvent event) {
        recentEvents.addFirst(event);
        while (recentEvents.size() > maxEvents) {
            recentEvents.removeLast();
        }
    }

    /**
     * Returns a snapshot of recent change events, newest first.
     *
     * @return recent config change events
     */
    public synchronized List<ConfigChangeEvent> getRecentEvents() {
        return new ArrayList<>(recentEvents);
    }

    /**
     * Clears the recorded audit trail.
     */
    public synchronized void clear() {
        recentEvents.clear();
    }
}
