package com.adhar.kit.kubernetes.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Published by {@link com.adhar.kit.kubernetes.config.ConfigMapReloadService} whenever
 * a watched ConfigMap is added, modified, or deleted - backing the
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesConfigMap#watch()} attribute.
 *
 * <p>Listen for this event (e.g. with {@code @EventListener}) to refresh
 * configuration-derived state when a ConfigMap changes.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Getter
public class ConfigMapChangedEvent extends ApplicationEvent {

    private final String name;
    private final String namespace;
    private final Map<String, String> data;
    private final ChangeType changeType;

    public ConfigMapChangedEvent(Object source, String name, String namespace,
                                  Map<String, String> data, ChangeType changeType) {
        super(source);
        this.name = name;
        this.namespace = namespace;
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        this.changeType = changeType;
    }
}
