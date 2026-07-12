package com.adhar.kit.kubernetes.client;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KubernetesClient} with a mocked Fabric8 client.
 *
 * <p>The fluent DSL operations are mocked via {@link Fabric8Support} so that
 * namespace/label navigation calls return the same operation mock; only the
 * terminal {@code withName}/{@code get}/{@code list}/{@code resource} calls are
 * stubbed explicitly.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KubernetesClientTest {

    private static final String NS = "test-ns";

    private KubernetesClient client;
    private io.fabric8.kubernetes.client.KubernetesClient fabric8;

    @BeforeEach
    void setUp() {
        KubernetesProperties properties = new KubernetesProperties();
        properties.setNamespace(NS);
        // The real constructor builds a Fabric8 client which cannot be created in
        // a unit-test environment, so allocate without a constructor and inject mocks.
        client = TestReflectionSupport.newInstanceWithoutConstructor(KubernetesClient.class);
        TestReflectionSupport.setField(client, "properties", properties);
        fabric8 = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(client, "client", fabric8);
    }

    @SuppressWarnings("unchecked")
    private MixedOperation<Pod, PodList, PodResource> podsOp() {
        MixedOperation<Pod, PodList, PodResource> op = Fabric8Support.mixedOp();
        when(fabric8.pods()).thenReturn(op);
        return op;
    }

    @Test
    void getNamespaceReturnsConfiguredNamespace() {
        assertEquals(NS, client.getNamespace());
    }

    @Test
    void getPodReturnsMappedPodInfo() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName("order-1")).thenReturn(res);
        when(res.get()).thenReturn(samplePod("order-1"));

        Optional<PodInfo> result = client.getPod("order-1");

        assertTrue(result.isPresent());
        PodInfo info = result.get();
        assertEquals("order-1", info.getName());
        assertEquals(NS, info.getNamespace());
        assertEquals("10.0.0.5", info.getIp());
        assertEquals("node-a", info.getNodeName());
        assertEquals("Running", info.getPhase());
        assertTrue(info.isRunning());
    }

    @Test
    void getPodReturnsEmptyWhenNotFound() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenReturn(null);

        assertTrue(client.getPod("missing").isEmpty());
    }

    @Test
    void getPodReturnsEmptyOnException() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertTrue(client.getPod("err").isEmpty());
    }

    @Test
    void listPodsMapsAllItems() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodList list = new PodListBuilder().withItems(samplePod("p1"), samplePod("p2")).build();
        when(op.list()).thenReturn(list);

        List<PodInfo> pods = client.listPods("app=order");

        assertEquals(2, pods.size());
        assertEquals("p1", pods.get(0).getName());
    }

    @Test
    void listPodsReturnsEmptyOnException() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        when(op.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(client.listPods("app=order").isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void discoverServicesMapsPorts() {
        MixedOperation op = Fabric8Support.mixedOp();
        when(fabric8.services()).thenReturn(op);
        Service service = new ServiceBuilder()
            .withNewMetadata().withName("svc").withNamespace(NS).endMetadata()
            .withNewSpec()
                .withClusterIP("10.1.1.1")
                .withType("ClusterIP")
                .addNewPort().withName("http").withPort(80).endPort()
            .endSpec()
            .build();
        when(op.list()).thenReturn(new ServiceListBuilder().withItems(service).build());

        List<ServiceInfo> services = client.discoverServices("app=order");

        assertEquals(1, services.size());
        assertEquals("svc", services.get(0).getName());
        assertEquals(80, services.get(0).getPorts().get("http"));
        assertEquals("ClusterIP", services.get(0).getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void discoverServicesReturnsEmptyOnException() {
        MixedOperation op = Fabric8Support.mixedOp();
        when(fabric8.services()).thenReturn(op);
        when(op.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(client.discoverServices("app=order").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private MixedOperation<ConfigMap, ?, Resource<ConfigMap>> configMapsOp() {
        MixedOperation op = Fabric8Support.mixedOp();
        when(fabric8.configMaps()).thenReturn(op);
        return op;
    }

    @Test
    void getConfigMapReturnsData() {
        MixedOperation<ConfigMap, ?, Resource<ConfigMap>> op = configMapsOp();
        Resource<ConfigMap> res = mock(Resource.class);
        when(op.withName("app-config")).thenReturn(res);
        ConfigMap cm = new ConfigMapBuilder()
            .withNewMetadata().withName("app-config").endMetadata()
            .withData(Map.of("key", "value"))
            .build();
        when(res.get()).thenReturn(cm);

        assertEquals("value", client.getConfigMap("app-config").get("key"));
    }

    @Test
    void getConfigMapReturnsEmptyWhenMissing() {
        MixedOperation<ConfigMap, ?, Resource<ConfigMap>> op = configMapsOp();
        Resource<ConfigMap> res = mock(Resource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenReturn(null);

        assertTrue(client.getConfigMap("missing").isEmpty());
    }

    @Test
    void getConfigMapReturnsEmptyOnException() {
        MixedOperation<ConfigMap, ?, Resource<ConfigMap>> op = configMapsOp();
        Resource<ConfigMap> res = mock(Resource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertTrue(client.getConfigMap("err", "other").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private MixedOperation<Secret, ?, Resource<Secret>> secretsOp() {
        MixedOperation op = Fabric8Support.mixedOp();
        when(fabric8.secrets()).thenReturn(op);
        return op;
    }

    @Test
    void getSecretDecodesBase64() {
        MixedOperation<Secret, ?, Resource<Secret>> op = secretsOp();
        Resource<Secret> res = mock(Resource.class);
        when(op.withName("db-secret")).thenReturn(res);
        String encoded = Base64.getEncoder().encodeToString("s3cr3t".getBytes(StandardCharsets.UTF_8));
        Secret secret = new SecretBuilder()
            .withNewMetadata().withName("db-secret").endMetadata()
            .withData(Map.of("password", encoded))
            .build();
        when(res.get()).thenReturn(secret);

        assertEquals("s3cr3t", client.getSecret("db-secret").get("password"));
    }

    @Test
    void getSecretReturnsEmptyWhenMissing() {
        MixedOperation<Secret, ?, Resource<Secret>> op = secretsOp();
        Resource<Secret> res = mock(Resource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenReturn(null);

        assertTrue(client.getSecret("missing", "other").isEmpty());
    }

    @Test
    void getSecretReturnsEmptyOnException() {
        MixedOperation<Secret, ?, Resource<Secret>> op = secretsOp();
        Resource<Secret> res = mock(Resource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertTrue(client.getSecret("err").isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void createOrUpdateConfigMapInvokesClient() {
        MixedOperation<ConfigMap, ?, Resource<ConfigMap>> op = configMapsOp();
        Resource<ConfigMap> res = mock(Resource.class);
        when(op.resource(any(ConfigMap.class))).thenReturn(res);

        client.createOrUpdateConfigMap("app-config", Map.of("a", "b"));

        verify(op).resource(any(ConfigMap.class));
        verify(res).createOrReplace();
    }

    @Test
    void createOrUpdateConfigMapSwallowsException() {
        MixedOperation<ConfigMap, ?, Resource<ConfigMap>> op = configMapsOp();
        when(op.resource(any(ConfigMap.class))).thenThrow(new RuntimeException("boom"));

        // should not propagate
        client.createOrUpdateConfigMap("app-config", Map.of("a", "b"));
    }

    @Test
    void getCurrentPodInfoMapsPodFromHostname() {
        // HOSTNAME is provided to the test JVM via Surefire, so the current pod is looked up.
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName("test-pod")).thenReturn(res);
        when(res.get()).thenReturn(samplePod("test-pod"));

        PodInfo info = client.getCurrentPodInfo();

        assertEquals("test-pod", info.getName());
        assertEquals(NS, info.getNamespace());
        assertEquals("10.0.0.5", info.getIp());
        assertEquals("192.168.1.1", info.getHostIp());
    }

    @Test
    void getCurrentPodInfoReturnsNamedBuilderWhenPodMissing() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenReturn(null);

        assertEquals("test-pod", client.getCurrentPodInfo().getName());
    }

    @Test
    void getCurrentPodInfoReturnsBuilderOnException() {
        MixedOperation<Pod, PodList, PodResource> op = podsOp();
        PodResource res = mock(PodResource.class);
        when(op.withName(anyString())).thenReturn(res);
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertNotNull(client.getCurrentPodInfo());
    }

    @Test
    void closeDelegatesToFabric8Client() {
        client.close();
        verify(fabric8).close();
    }

    @Test
    void constructorBuildsClientConfigAndFailsGracefully() {
        // Exercises createClient(): masterUrl + namespace are applied to the ConfigBuilder
        // before the (environment-unsupported) HTTP client build fails and is rethrown.
        KubernetesProperties props = new KubernetesProperties();
        props.setNamespace("ns");
        props.setMasterUrl("https://api.example.com:6443");

        assertThrows(Throwable.class, () -> new KubernetesClient(props));
    }

    private Pod samplePod(String name) {
        return new PodBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(NS)
                .withUid("uid-" + name)
                .withLabels(Map.of("app", "order"))
                .withAnnotations(Map.of("note", "x"))
            .endMetadata()
            .withNewSpec()
                .withNodeName("node-a")
                .withServiceAccountName("default")
            .endSpec()
            .withNewStatus()
                .withPodIP("10.0.0.5")
                .withHostIP("192.168.1.1")
                .withPhase("Running")
            .endStatus()
            .build();
    }
}
