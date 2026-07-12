package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.model.NamespaceInfo;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.NamespaceListBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NamespaceService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamespaceServiceTest {

    private NamespaceService service;
    private NonNamespaceOperation namespaces;

    @BeforeEach
    void setUp() {
        service = TestReflectionSupport.newInstanceWithoutConstructor(NamespaceService.class);
        io.fabric8.kubernetes.client.KubernetesClient fabric8 =
            mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);
        namespaces = Fabric8Support.nonNamespaceOp();
        when(fabric8.namespaces()).thenReturn(namespaces);
    }

    private Resource namespaceResource() {
        Resource res = mock(Resource.class);
        when(namespaces.withName(anyString())).thenReturn(res);
        return res;
    }

    private Namespace sampleNamespace(String name, String phase) {
        return new NamespaceBuilder()
            .withNewMetadata().withName(name).withLabels(Map.of("team", "platform")).endMetadata()
            .withNewStatus().withPhase(phase).endStatus()
            .build();
    }

    @Test
    void getNamespaceMapsFields() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(sampleNamespace("production", "Active"));

        NamespaceInfo info = service.getNamespace("production");

        assertEquals("production", info.getName());
        assertEquals("Active", info.getStatus());
        assertTrue(info.isActive());
        assertEquals("platform", info.getLabels().get("team"));
    }

    @Test
    void getNamespaceUsesUnknownStatusWhenNoStatus() {
        Resource res = namespaceResource();
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName("ns").endMetadata().build();
        when(res.get()).thenReturn(ns);

        assertEquals("Unknown", service.getNamespace("ns").getStatus());
    }

    @Test
    void getNamespaceReturnsNullWhenMissing() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(null);

        assertNull(service.getNamespace("missing"));
    }

    @Test
    void getNamespaceReturnsNullOnException() {
        Resource res = namespaceResource();
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertNull(service.getNamespace("err"));
    }

    @Test
    void listNamespacesMapsItems() {
        NamespaceList list = new NamespaceListBuilder()
            .withItems(sampleNamespace("a", "Active"), sampleNamespace("b", "Terminating"))
            .build();
        when(namespaces.list()).thenReturn(list);

        List<NamespaceInfo> infos = service.listNamespaces();

        assertEquals(2, infos.size());
    }

    @Test
    void listNamespacesReturnsEmptyOnException() {
        when(namespaces.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(service.listNamespaces().isEmpty());
    }

    @Test
    void createNamespaceReturnsTrueOnSuccess() {
        Resource res = mock(Resource.class);
        when(namespaces.resource(any(Namespace.class))).thenReturn(res);

        assertTrue(service.createNamespace("new-ns", Map.of("env", "test")));
        verify(res).create();
    }

    @Test
    void createNamespaceReturnsFalseOnException() {
        when(namespaces.resource(any(Namespace.class))).thenThrow(new RuntimeException("boom"));

        assertFalse(service.createNamespace("new-ns", Map.of()));
    }

    @Test
    void deleteNamespaceReturnsTrueOnSuccess() {
        Resource res = namespaceResource();

        assertTrue(service.deleteNamespace("old-ns"));
        verify(res).delete();
    }

    @Test
    void deleteNamespaceReturnsFalseOnException() {
        Resource res = namespaceResource();
        when(res.delete()).thenThrow(new RuntimeException("boom"));

        assertFalse(service.deleteNamespace("old-ns"));
    }

    @Test
    void namespaceExistsTrueWhenPresent() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(sampleNamespace("ns", "Active"));

        assertTrue(service.namespaceExists("ns"));
    }

    @Test
    void namespaceExistsFalseWhenAbsent() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(null);

        assertFalse(service.namespaceExists("ns"));
    }

    @Test
    void namespaceExistsFalseOnException() {
        Resource res = namespaceResource();
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertFalse(service.namespaceExists("ns"));
    }

    @Test
    void getNamespaceLabelsReturnsLabels() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(sampleNamespace("ns", "Active"));

        assertEquals("platform", service.getNamespaceLabels("ns").get("team"));
    }

    @Test
    void getNamespaceLabelsReturnsEmptyWhenMissing() {
        Resource res = namespaceResource();
        when(res.get()).thenReturn(null);

        assertTrue(service.getNamespaceLabels("ns").isEmpty());
    }

    @Test
    void constructorFailsGracefullyInTestEnvironment() {
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, NamespaceService::new);
    }
}
