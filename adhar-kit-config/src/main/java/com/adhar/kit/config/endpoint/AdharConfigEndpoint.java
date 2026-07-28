package com.adhar.kit.config.endpoint;

import com.adhar.kit.config.audit.ConfigChangeAuditPublisher;
import com.adhar.kit.config.audit.ConfigChangeEvent;
import com.adhar.kit.config.audit.ConfigMasking;
import com.adhar.kit.config.featureflag.FeatureFlag;
import com.adhar.kit.config.featureflag.FeatureFlagService;
import com.adhar.kit.config.manager.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Actuator endpoint exposing the state of the Adhar config module.
 *
 * <p>{@code GET /actuator/adharconfig} returns:</p>
 * <ul>
 *   <li><b>sources</b> - registered config sources with health status;</li>
 *   <li><b>properties</b> - merged configuration with secret-looking values masked;</li>
 *   <li><b>featureFlags</b> - current feature flag definitions;</li>
 *   <li><b>recentChanges</b> - recent audited config change events.</li>
 * </ul>
 *
 * <p>Only active when Spring Boot Actuator is on the classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Endpoint(id = "adharconfig")
public class AdharConfigEndpoint {

    private final ConfigManager configManager;
    private final ObjectProvider<FeatureFlagService> featureFlagService;
    private final ObjectProvider<ConfigChangeAuditPublisher> auditPublisher;

    /**
     * Creates the endpoint.
     *
     * @param configManager the config manager
     * @param featureFlagService optional feature-flag service
     * @param auditPublisher optional audit publisher
     */
    public AdharConfigEndpoint(ConfigManager configManager,
                               ObjectProvider<FeatureFlagService> featureFlagService,
                               ObjectProvider<ConfigChangeAuditPublisher> auditPublisher) {
        this.configManager = configManager;
        this.featureFlagService = featureFlagService;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Full snapshot of config sources, masked properties, flags and recent changes.
     *
     * @return an ordered map describing the config module state
     */
    @ReadOperation
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sources", sources());
        result.put("properties", maskedProperties());
        result.put("featureFlags", featureFlags());
        result.put("recentChanges", recentChanges());
        return result;
    }

    private List<Map<String, Object>> sources() {
        List<Map<String, Object>> list = new ArrayList<>();
        configManager.getHealthStatus().forEach((type, healthy) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("healthy", healthy);
            list.add(entry);
        });
        return list;
    }

    private Map<String, Object> maskedProperties() {
        Map<String, Object> masked = new TreeMap<>();
        configManager.getPropertiesWithPrefix("").forEach((key, value) ->
                masked.put(key, ConfigMasking.maskIfSecret(key, value)));
        return masked;
    }

    private Map<String, Object> featureFlags() {
        FeatureFlagService service = featureFlagService.getIfAvailable();
        if (service == null) {
            return Map.of();
        }
        Map<String, Object> result = new TreeMap<>();
        for (Map.Entry<String, FeatureFlag> entry : service.getFlags().entrySet()) {
            FeatureFlag flag = entry.getValue();
            Map<String, Object> flagMap = new LinkedHashMap<>();
            flagMap.put("enabled", flag.enabled());
            flagMap.put("rolloutPercentage", flag.rolloutPercentage());
            flagMap.put("allowList", flag.allowList());
            flagMap.put("denyList", flag.denyList());
            result.put(entry.getKey(), flagMap);
        }
        return result;
    }

    private List<Map<String, Object>> recentChanges() {
        ConfigChangeAuditPublisher publisher = auditPublisher.getIfAvailable();
        if (publisher == null) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConfigChangeEvent event : publisher.getRecentEvents()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", event.getKey());
            entry.put("oldValue", event.getOldValue());
            entry.put("newValue", event.getNewValue());
            entry.put("secret", event.isSecret());
            entry.put("changedAt", event.getChangedAt().toString());
            list.add(entry);
        }
        return list;
    }
}
