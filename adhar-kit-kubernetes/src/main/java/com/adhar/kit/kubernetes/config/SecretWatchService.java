package com.adhar.kit.kubernetes.config;

import com.adhar.kit.kubernetes.event.ChangeType;
import com.adhar.kit.kubernetes.event.SecretChangedEvent;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Backs the {@code watch}/{@code reloadOnChange} attributes of
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesSecret} using a Fabric8
 * {@link SharedIndexInformer}. Publishes a {@link SecretChangedEvent} on the Spring
 * {@link ApplicationEventPublisher} whenever a watched Secret changes.
 *
 * <p><b>Graceful degradation:</b> when not running in a cluster, {@link #watch}
 * logs a warning and does nothing instead of throwing.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class SecretWatchService {

    private final ApplicationEventPublisher eventPublisher;
    private final io.fabric8.kubernetes.client.KubernetesClient client;
    private final Map<String, SharedIndexInformer<Secret>> informers = new ConcurrentHashMap<>();

    public SecretWatchService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.client = createFabric8Client();
    }

    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        try {
            return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
        } catch (Throwable e) {
            log.warn("Failed to create Kubernetes client for Secret watching - " +
                    "Secret reload-on-change will be disabled", e);
            return null;
        }
    }

    /**
     * Starts watching a Secret for changes, publishing {@link SecretChangedEvent}
     * on add/update/delete. Idempotent - calling this again for the same name/namespace
     * is a no-op.
     *
     * @param name      Secret name
     * @param namespace namespace the Secret lives in
     */
    public void watch(String name, String namespace) {
        if (client == null) {
            log.warn("Kubernetes client unavailable; cannot watch Secret {}/{}", namespace, name);
            return;
        }
        String key = key(name, namespace);
        if (informers.containsKey(key)) {
            return;
        }
        try {
            SharedIndexInformer<Secret> informer = client.secrets()
                    .inNamespace(namespace)
                    .withName(name)
                    .inform(new ResourceEventHandler<>() {
                        @Override
                        public void onAdd(Secret secret) {
                            publish(secret, ChangeType.ADDED);
                        }

                        @Override
                        public void onUpdate(Secret oldSecret, Secret newSecret) {
                            publish(newSecret, ChangeType.MODIFIED);
                        }

                        @Override
                        public void onDelete(Secret secret, boolean deletedFinalStateUnknown) {
                            publish(secret, ChangeType.DELETED);
                        }
                    });
            informers.put(key, informer);
            log.info("Watching Secret {}/{} for changes", namespace, name);
        } catch (Exception e) {
            log.warn("Failed to start watch for Secret {}/{} - reload-on-change disabled", namespace, name, e);
        }
    }

    private void publish(Secret secret, ChangeType changeType) {
        try {
            String name = secret.getMetadata() != null ? secret.getMetadata().getName() : null;
            String namespace = secret.getMetadata() != null ? secret.getMetadata().getNamespace() : null;
            Map<String, String> data = decode(secret);
            eventPublisher.publishEvent(new SecretChangedEvent(this, name, namespace, data, changeType));
            log.debug("Published SecretChangedEvent for {}/{} ({})", namespace, name, changeType);
        } catch (Exception e) {
            log.error("Failed to publish SecretChangedEvent", e);
        }
    }

    private Map<String, String> decode(Secret secret) {
        if (secret.getData() == null) {
            return Map.of();
        }
        return secret.getData().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new String(Base64.getDecoder().decode(e.getValue()))));
    }

    /**
     * Stops watching a specific Secret.
     *
     * @param name      Secret name
     * @param namespace namespace the Secret lives in
     */
    public void stopWatching(String name, String namespace) {
        SharedIndexInformer<Secret> informer = informers.remove(key(name, namespace));
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
     * @param name      Secret name
     * @param namespace namespace the Secret lives in
     * @return true if a watch is currently active for the given Secret
     */
    public boolean isWatching(String name, String namespace) {
        return informers.containsKey(key(name, namespace));
    }

    /**
     * @return the number of Secrets currently being watched; useful as a
     * health/observability signal for the watch subsystem
     */
    public int getActiveWatchCount() {
        return informers.size();
    }

    private static String key(String name, String namespace) {
        return namespace + "/" + name;
    }
}
