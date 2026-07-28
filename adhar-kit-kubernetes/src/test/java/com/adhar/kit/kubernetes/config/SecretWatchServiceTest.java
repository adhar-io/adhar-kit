package com.adhar.kit.kubernetes.config;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.event.ChangeType;
import com.adhar.kit.kubernetes.event.SecretChangedEvent;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * Unit tests for {@link SecretWatchService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecretWatchServiceTest {

    private static final String NS = "test-ns";

    private SecretWatchService service;
    private ApplicationEventPublisher eventPublisher;
    private KubernetesClient fabric8;
    private MixedOperation<Secret, ?, Resource<Secret>> secretsOp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = TestReflectionSupport.newInstanceWithoutConstructor(SecretWatchService.class);
        TestReflectionSupport.setField(service, "eventPublisher", eventPublisher);
        fabric8 = mock(KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);
        // Objenesis-allocated instances skip field initializers, so the informers map
        // must be set explicitly.
        TestReflectionSupport.setField(service, "informers", new java.util.concurrent.ConcurrentHashMap<>());

        secretsOp = Fabric8Support.mixedOp();
        when(fabric8.secrets()).thenReturn((MixedOperation) secretsOp);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ResourceEventHandler<Secret>> stubInform(String name, SharedIndexInformer<Secret> informer) {
        Resource<Secret> resource = mock(Resource.class);
        when(secretsOp.withName(name)).thenReturn(resource);
        ArgumentCaptor<ResourceEventHandler<Secret>> captor = ArgumentCaptor.forClass(ResourceEventHandler.class);
        when(resource.inform(captor.capture())).thenReturn(informer);
        return captor;
    }

    private Secret secret(String name, Map<String, String> plainTextData) {
        Map<String, String> encoded = plainTextData.isEmpty() ? Map.of()
                : Map.of("password", Base64.getEncoder().encodeToString(
                        plainTextData.get("password").getBytes(StandardCharsets.UTF_8)));
        return new SecretBuilder()
                .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
                .withData(encoded)
                .build();
    }

    @Test
    void watchRegistersInformerAndPublishesDecodedEventOnAdd() {
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<Secret>> captor = stubInform("db-secret", informer);

        service.watch("db-secret", NS);
        assertTrue(service.isWatching("db-secret", NS));

        captor.getValue().onAdd(secret("db-secret", Map.of("password", "s3cr3t")));

        ArgumentCaptor<SecretChangedEvent> eventCaptor = ArgumentCaptor.forClass(SecretChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SecretChangedEvent event = eventCaptor.getValue();
        assertEquals("db-secret", event.getName());
        assertEquals(NS, event.getNamespace());
        assertEquals("s3cr3t", event.getData().get("password"));
        assertEquals(ChangeType.ADDED, event.getChangeType());
    }

    @Test
    void watchPublishesModifiedAndDeletedEvents() {
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<Secret>> captor = stubInform("db-secret", informer);
        service.watch("db-secret", NS);

        captor.getValue().onUpdate(secret("db-secret", Map.of("password", "old")),
                secret("db-secret", Map.of("password", "new")));
        captor.getValue().onDelete(secret("db-secret", Map.of()), false);

        ArgumentCaptor<SecretChangedEvent> eventCaptor = ArgumentCaptor.forClass(SecretChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertEquals(ChangeType.MODIFIED, eventCaptor.getAllValues().get(0).getChangeType());
        assertEquals("new", eventCaptor.getAllValues().get(0).getData().get("password"));
        assertEquals(ChangeType.DELETED, eventCaptor.getAllValues().get(1).getChangeType());
        assertTrue(eventCaptor.getAllValues().get(1).getData().isEmpty());
    }

    @Test
    void watchIsIdempotent() {
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        stubInform("db-secret", informer);

        service.watch("db-secret", NS);
        service.watch("db-secret", NS);

        verify(secretsOp, times(1)).withName("db-secret");
    }

    @Test
    void getActiveWatchCountReflectsRegisteredInformers() {
        assertEquals(0, service.getActiveWatchCount());
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        stubInform("db-secret", informer);

        service.watch("db-secret", NS);

        assertEquals(1, service.getActiveWatchCount());
    }

    @Test
    void watchLogsAndSkipsWhenClientUnavailable() {
        TestReflectionSupport.setField(service, "client", null);

        service.watch("missing-client", NS);

        assertFalse(service.isWatching("missing-client", NS));
    }

    @Test
    void watchSwallowsExceptionFromInformerSetup() {
        when(secretsOp.withName(anyString())).thenThrow(new RuntimeException("boom"));

        service.watch("db-secret", NS);

        assertFalse(service.isWatching("db-secret", NS));
    }

    @Test
    void stopWatchingClosesInformerAndRemovesIt() {
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        stubInform("db-secret", informer);
        service.watch("db-secret", NS);

        service.stopWatching("db-secret", NS);

        verify(informer).close();
        assertFalse(service.isWatching("db-secret", NS));
    }

    @Test
    void stopAllClosesEveryInformer() {
        SharedIndexInformer<Secret> informer1 = mock(SharedIndexInformer.class);
        stubInform("s1", informer1);
        service.watch("s1", NS);

        Resource<Secret> resource2 = mock(Resource.class);
        when(secretsOp.withName("s2")).thenReturn(resource2);
        SharedIndexInformer<Secret> informer2 = mock(SharedIndexInformer.class);
        when(resource2.inform(any(ResourceEventHandler.class))).thenReturn(informer2);
        service.watch("s2", NS);

        service.stopAll();

        verify(informer1).close();
        verify(informer2).close();
        assertFalse(service.isWatching("s1", NS));
        assertFalse(service.isWatching("s2", NS));
    }

    @Test
    void publishSwallowsEventPublisherException() {
        SharedIndexInformer<Secret> informer = mock(SharedIndexInformer.class);
        ArgumentCaptor<ResourceEventHandler<Secret>> captor = stubInform("db-secret", informer);
        service.watch("db-secret", NS);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(eventPublisher).publishEvent(any());

        captor.getValue().onAdd(secret("db-secret", Map.of("password", "x")));
        // must not propagate
    }

    @Test
    void constructorDegradesGracefullyWithoutCluster() {
        SecretWatchService realService = new SecretWatchService(eventPublisher);
        realService.watch("db-secret", NS);
        assertFalse(realService.isWatching("db-secret", NS));
    }
}
