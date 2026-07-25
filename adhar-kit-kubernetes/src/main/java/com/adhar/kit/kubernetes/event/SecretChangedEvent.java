package com.adhar.kit.kubernetes.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Published by {@link com.adhar.kit.kubernetes.config.SecretWatchService} whenever a
 * watched Secret is added, modified, or deleted - backing the
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesSecret#watch()} attribute.
 *
 * <p>The {@link #getData()} map contains base64-decoded values, consistent with
 * {@link com.adhar.kit.kubernetes.client.KubernetesClient#getSecret(String)}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Getter
public class SecretChangedEvent extends ApplicationEvent {

    private final String name;
    private final String namespace;
    private final Map<String, String> data;
    private final ChangeType changeType;

    public SecretChangedEvent(Object source, String name, String namespace,
                               Map<String, String> data, ChangeType changeType) {
        super(source);
        this.name = name;
        this.namespace = namespace;
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        this.changeType = changeType;
    }
}
