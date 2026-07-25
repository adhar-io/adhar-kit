package com.adhar.kit.kubernetes.config;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.event.ChangeType;
import com.adhar.kit.kubernetes.event.ConfigMapChangedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigMapReloadService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigMapReloadServiceTest {

    private static final String NS = "test-ns";

    private ConfigMapReloadService service;
    private ApplicationEventPublisher eventPublisher;
    private KubernetesClient fabric8;
    private MixedOperation<ConfigMap, ?, Resource<ConfigMap>> configMapsOp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = TestReflectionSupport.newInstanceWithoutConstructor(ConfigMapReloadService.class);
        TestReflectionSupport.setField(service, "eventPublisher", eventPublisher);
        fabric8 = mock(KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);
        // Objenesis-allocated instances skip field initializers, so the informers map
        // must be set explicitly.
        TestReflectionSupport.setField(service, "informers", new java.util.concurrent.ConcurrentHashMap<>());

        configMapsOp = Fabric8Support.mixedOp();
        when(fabric8.configMaps()).thenReturn((MixedOperation) configMapsOp);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ResourceEventHandler<ConfigMap>> stubInform(String name, SharedIndexInformer<ConfigMap> informer) {
        Resource<ConfigMap> resource = mock(Resource.class);
        when(configMapsOp.withName(name)).thenReturn(resource);
        ArgumentCaptor<ResourceEventHandler<ConfigMap>> captor = ArgumentCaptor.forClass(ResourceEventHandler.class);
        when(resource.inform(captor.capture())).thenReturn(informer);
        return captor;
    }

    private ConfigMap configMap(String name, Map<String, String> data) {
        return new ConfigMapBuilder()
                .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
                .withData(data)
                .build();
    }

    @Test
    void watchRegistersInformerAndPublishesEventsOnAdd() {
        SharedIndexInformer<ConfigMap> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<ConfigMap>> captor = stubInform("app-config", informer);

        service.watch("app-config", NS);

        assertTrue(service.isWatching("app-config", NS));
        captor.getValue().onAdd(configMap("app-config", Map.of("k", "v")));

        ArgumentCaptor<ConfigMapChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConfigMapChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigMapChangedEvent event = eventCaptor.getValue();
        assertEquals("app-config", event.getName());
        assertEquals(NS, event.getNamespace());
        assertEquals("v", event.getData().get("k"));
        assertEquals(ChangeType.ADDED, event.getChangeType());
    }

    @Test
    void watchPublishesModifiedAndDeletedEvents() {
        SharedIndexInformer<ConfigMap> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<ConfigMap>> captor = stubInform("app-config", informer);
        service.watch("app-config", NS);

        captor.getValue().onUpdate(configMap("app-config", Map.of("k", "old")),
                configMap("app-config", Map.of("k", "new")));
        captor.getValue().onDelete(configMap("app-config", Map.of()), false);

        ArgumentCaptor<ConfigMapChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConfigMapChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertEquals(ChangeType.MODIFIED, eventCaptor.getAllValues().get(0).getChangeType());
        assertEquals("new", eventCaptor.getAllValues().get(0).getData().get("k"));
        assertEquals(ChangeType.DELETED, eventCaptor.getAllValues().get(1).getChangeType());
    }

    @Test
    void watchIsIdempotent() {
        SharedIndexInformer<ConfigMap> informer = mock(SharedIndexInformer.class);
        stubInform("app-config", informer);

        service.watch("app-config", NS);
        service.watch("app-config", NS);

        verify(configMapsOp, times(1)).withName("app-config");
    }

    @Test
    void watchLogsAndSkipsWhenClientUnavailable() {
        TestReflectionSupport.setField(service, "client", null);

        service.watch("missing-client", NS);

        assertFalse(service.isWatching("missing-client", NS));
    }

    @Test
    void watchSwallowsExceptionFromInformerSetup() {
        when(configMapsOp.withName(anyString())).thenThrow(new RuntimeException("boom"));

        service.watch("app-config", NS);

        assertFalse(service.isWatching("app-config", NS));
    }

    @Test
    void stopWatchingClosesInformerAndRemovesIt() {
        SharedIndexInformer<ConfigMap> informer = mock(SharedIndexInformer.class);
        stubInform("app-config", informer);
        service.watch("app-config", NS);

        service.stopWatching("app-config", NS);

        verify(informer).close();
        assertFalse(service.isWatching("app-config", NS));
    }

    @Test
    void stopAllClosesEveryInformer() {
        SharedIndexInformer<ConfigMap> informer1 = mock(SharedIndexInformer.class);
        stubInform("cm1", informer1);
        service.watch("cm1", NS);

        Resource<ConfigMap> resource2 = mock(Resource.class);
        when(configMapsOp.withName("cm2")).thenReturn(resource2);
        SharedIndexInformer<ConfigMap> informer2 = mock(SharedIndexInformer.class);
        when(resource2.inform(any(ResourceEventHandler.class))).thenReturn(informer2);
        service.watch("cm2", NS);

        service.stopAll();

        verify(informer1).close();
        verify(informer2).close();
        assertFalse(service.isWatching("cm1", NS));
        assertFalse(service.isWatching("cm2", NS));
    }

    @Test
    void publishSwallowsEventPublisherException() {
        SharedIndexInformer<ConfigMap> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<ConfigMap>> captor = stubInform("app-config", informer);
        service.watch("app-config", NS);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(eventPublisher).publishEvent(any());

        captor.getValue().onAdd(configMap("app-config", Map.of()));
        // must not propagate
    }

    @Test
    void constructorDegradesGracefullyWithoutCluster() {
        ConfigMapReloadService realService = new ConfigMapReloadService(eventPublisher);
        // No real cluster available in this test environment - client creation must have
        // been caught internally, so watch() logs a warning instead of throwing.
        realService.watch("app-config", NS);
        assertFalse(realService.isWatching("app-config", NS));
    }
}
