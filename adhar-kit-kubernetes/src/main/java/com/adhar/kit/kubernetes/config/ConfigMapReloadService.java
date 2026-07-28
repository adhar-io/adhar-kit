package com.adhar.kit.kubernetes.config;

import com.adhar.kit.kubernetes.event.ChangeType;
import com.adhar.kit.kubernetes.event.ConfigMapChangedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backs the {@code watch}/{@code reloadOnChange} attributes of
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesConfigMap} using a Fabric8
 * {@link SharedIndexInformer}. Publishes a {@link ConfigMapChangedEvent} on the Spring
 * {@link ApplicationEventPublisher} whenever a watched ConfigMap changes.
 *
 * <p><b>Graceful degradation:</b> when not running in a cluster, {@link #watch}
 * logs a warning and does nothing instead of throwing.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class ConfigMapReloadService {

    private final ApplicationEventPublisher eventPublisher;
    private final io.fabric8.kubernetes.client.KubernetesClient client;
    private final Map<String, SharedIndexInformer<ConfigMap>> informers = new ConcurrentHashMap<>();

    public ConfigMapReloadService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.client = createFabric8Client();
    }

    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        try {
            return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
        } catch (Throwable e) {
            log.warn("Failed to create Kubernetes client for ConfigMap watching - " +
                    "ConfigMap reload-on-change will be disabled", e);
            return null;
        }
    }

    /**
     * Starts watching a ConfigMap for changes, publishing {@link ConfigMapChangedEvent}
     * on add/update/delete. Idempotent - calling this again for the same name/namespace
     * is a no-op.
     *
     * @param name      ConfigMap name
     * @param namespace namespace the ConfigMap lives in
     */
    public void watch(String name, String namespace) {
        if (client == null) {
            log.warn("Kubernetes client unavailable; cannot watch ConfigMap {}/{}", namespace, name);
            return;
        }
        String key = key(name, namespace);
        if (informers.containsKey(key)) {
            return;
        }
        try {
            SharedIndexInformer<ConfigMap> informer = client.configMaps()
                    .inNamespace(namespace)
                    .withName(name)
                    .inform(new ResourceEventHandler<>() {
                        @Override
                        public void onAdd(ConfigMap configMap) {
                            publish(configMap, ChangeType.ADDED);
                        }

                        @Override
                        public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
                            publish(newConfigMap, ChangeType.MODIFIED);
                        }

                        @Override
                        public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
                            publish(configMap, ChangeType.DELETED);
                        }
                    });
            informers.put(key, informer);
            log.info("Watching ConfigMap {}/{} for changes", namespace, name);
        } catch (Exception e) {
            log.warn("Failed to start watch for ConfigMap {}/{} - reload-on-change disabled", namespace, name, e);
        }
    }

    private void publish(ConfigMap configMap, ChangeType changeType) {
        try {
            String name = configMap.getMetadata() != null ? configMap.getMetadata().getName() : null;
            String namespace = configMap.getMetadata() != null ? configMap.getMetadata().getNamespace() : null;
            Map<String, String> data = configMap.getData() != null ? configMap.getData() : Map.of();
            eventPublisher.publishEvent(new ConfigMapChangedEvent(this, name, namespace, data, changeType));
            log.debug("Published ConfigMapChangedEvent for {}/{} ({})", namespace, name, changeType);
        } catch (Exception e) {
            log.error("Failed to publish ConfigMapChangedEvent", e);
        }
    }

    /**
     * Stops watching a specific ConfigMap.
     *
     * @param name      ConfigMap name
     * @param namespace namespace the ConfigMap lives in
     */
    public void stopWatching(String name, String namespace) {
        SharedIndexInformer<ConfigMap> informer = informers.remove(key(name, namespace));
        if (informer != null) {
            informer.close();
        }
    }

    /**
     * Stops all active watches, closing every informer.
     */
    public void stopAll() {
        informers.values().forEach(SharedIndexInformer::close);
        informers.clear();
    }

    /**
     * @param name      ConfigMap name
     * @param namespace namespace the ConfigMap lives in
     * @return true if a watch is currently active for the given ConfigMap
     */
    public boolean isWatching(String name, String namespace) {
        return informers.containsKey(key(name, namespace));
    }

    /**
     * @return the number of ConfigMaps currently being watched; useful as a
     * health/observability signal for the watch subsystem
     */
    public int getActiveWatchCount() {
        return informers.size();
    }

    private static String key(String name, String namespace) {
        return namespace + "/" + name;
    }
}
