package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.KubernetesConfigMap;
import com.adhar.kit.kubernetes.annotation.KubernetesSecret;
import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link KubernetesWatchBeanPostProcessor}.
 */
class KubernetesWatchBeanPostProcessorTest {

    @KubernetesConfigMap(name = "app-config", namespace = "cm-ns")
    static class WatchedConfigMapBean {
    }

    @KubernetesConfigMap(name = "app-config-2", watch = false)
    static class NonWatchedConfigMapBean {
    }

    @KubernetesSecret(name = "app-secret", namespace = "secret-ns", watch = true)
    static class WatchedSecretBean {
    }

    @KubernetesSecret(name = "app-secret-2")
    static class DefaultSecretBean {
    }

    static class PlainBean {
    }

    private KubernetesProperties properties;
    private ConfigMapReloadService configMapReloadService;
    private SecretWatchService secretWatchService;
    private KubernetesWatchBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new KubernetesProperties();
        configMapReloadService = mock(ConfigMapReloadService.class);
        secretWatchService = mock(SecretWatchService.class);
        processor = new KubernetesWatchBeanPostProcessor(properties, configMapReloadService, secretWatchService);
    }

    @Test
    void watchesConfigMapWhenAnnotationRequestsIt() {
        processor.postProcessAfterInitialization(new WatchedConfigMapBean(), "bean");

        verify(configMapReloadService).watch("app-config", "cm-ns");
    }

    @Test
    void skipsConfigMapWatchWhenAnnotationDisablesIt() {
        processor.postProcessAfterInitialization(new NonWatchedConfigMapBean(), "bean");

        verify(configMapReloadService, never()).watch(eq("app-config-2"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void skipsConfigMapWatchWhenGloballyDisabled() {
        properties.getConfigMap().setWatchEnabled(false);

        processor.postProcessAfterInitialization(new WatchedConfigMapBean(), "bean");

        verify(configMapReloadService, never()).watch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void watchesSecretWhenAnnotationRequestsIt() {
        processor.postProcessAfterInitialization(new WatchedSecretBean(), "bean");

        verify(secretWatchService).watch("app-secret", "secret-ns");
    }

    @Test
    void defaultSecretNamespaceFallsBackToProperties() {
        properties.setNamespace("configured-ns");

        processor.postProcessAfterInitialization(new DefaultSecretBean(), "bean");

        verify(secretWatchService).watch("app-secret-2", "configured-ns");
    }

    @Test
    void skipsSecretWatchWhenGloballyDisabled() {
        properties.getSecret().setWatchEnabled(false);

        processor.postProcessAfterInitialization(new WatchedSecretBean(), "bean");

        verify(secretWatchService, never()).watch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void ignoresPlainBeans() {
        Object result = processor.postProcessAfterInitialization(new PlainBean(), "bean");

        verify(configMapReloadService, never()).watch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(secretWatchService, never()).watch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }
}
