package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.KubernetesConfigMap;
import com.adhar.kit.kubernetes.annotation.KubernetesSecret;
import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.util.KubernetesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Backs the {@code watch = true} attribute of
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesConfigMap} and
 * {@link com.adhar.kit.kubernetes.annotation.KubernetesSecret}: for every annotated bean
 * discovered during context refresh, registers a watch with
 * {@link ConfigMapReloadService} / {@link SecretWatchService} respectively, so a
 * {@code ConfigMapChangedEvent} / {@code SecretChangedEvent} is published whenever the
 * underlying resource changes.
 *
 * <p>Watching is globally toggled via
 * {@code adhar.kubernetes.config-map.watch-enabled} /
 * {@code adhar.kubernetes.secret.watch-enabled} (see {@link KubernetesProperties}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class KubernetesWatchBeanPostProcessor implements BeanPostProcessor {

    private final KubernetesProperties properties;
    private final ConfigMapReloadService configMapReloadService;
    private final SecretWatchService secretWatchService;

    public KubernetesWatchBeanPostProcessor(KubernetesProperties properties,
                                             ConfigMapReloadService configMapReloadService,
                                             SecretWatchService secretWatchService) {
        this.properties = properties;
        this.configMapReloadService = configMapReloadService;
        this.secretWatchService = secretWatchService;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        KubernetesConfigMap configMapAnnotation = bean.getClass().getAnnotation(KubernetesConfigMap.class);
        if (configMapAnnotation != null && configMapAnnotation.watch()) {
            if (properties.getConfigMap().isWatchEnabled()) {
                String namespace = resolveNamespace(configMapAnnotation.namespace());
                configMapReloadService.watch(configMapAnnotation.name(), namespace);
            } else {
                log.debug("ConfigMap watch disabled globally; skipping watch for bean '{}'", beanName);
            }
        }

        KubernetesSecret secretAnnotation = bean.getClass().getAnnotation(KubernetesSecret.class);
        if (secretAnnotation != null && secretAnnotation.watch()) {
            if (properties.getSecret().isWatchEnabled()) {
                String namespace = resolveNamespace(secretAnnotation.namespace());
                secretWatchService.watch(secretAnnotation.name(), namespace);
            } else {
                log.debug("Secret watch disabled globally; skipping watch for bean '{}'", beanName);
            }
        }

        return bean;
    }

    private String resolveNamespace(String annotationNamespace) {
        if (annotationNamespace != null && !annotationNamespace.isBlank()) {
            return annotationNamespace;
        }
        if (properties.getNamespace() != null && !properties.getNamespace().isBlank()) {
            return properties.getNamespace();
        }
        return KubernetesUtils.getNamespace();
    }
}
