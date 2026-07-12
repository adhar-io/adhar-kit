package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ResourceMetrics;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuotaList;
import io.fabric8.kubernetes.api.model.ResourceQuotaListBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceMonitoringService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceMonitoringServiceTest {

    private static final String NS = "test-ns";

    private ResourceMonitoringService service;
    private KubernetesClient wrapper;
    private io.fabric8.kubernetes.client.KubernetesClient fabric8;
    private MixedOperation pods;

    @BeforeEach
    void setUp() {
        wrapper = mock(KubernetesClient.class);
        when(wrapper.getNamespace()).thenReturn(NS);
        service = TestReflectionSupport.newInstanceWithoutConstructor(ResourceMonitoringService.class);
        TestReflectionSupport.setField(service, "kubernetesClient", wrapper);
        fabric8 = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);
        pods = Fabric8Support.mixedOp();
        lenient().when(fabric8.pods()).thenReturn(pods);
    }

    private void stubPodGet(String name, Pod pod) {
        PodResource res = mock(PodResource.class);
        when(pods.withName(name)).thenReturn(res);
        when(res.get()).thenReturn(pod);
    }

    /**
     * A pod whose containers exercise every CPU/memory parsing branch.
     */
    private Pod richPod(String name) {
        return new PodBuilder()
            .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
            .withNewSpec()
                .addNewContainer().withName("c1")
                    .withNewResources()
                        .withRequests(Map.of("cpu", new Quantity("500m", ""),
                                             "memory", new Quantity("128Mi", "")))
                    .endResources()
                .endContainer()
                .addNewContainer().withName("c2")
                    .withNewResources()
                        .withRequests(Map.of("cpu", new Quantity("2", ""),
                                             "memory", new Quantity("2Gi", "")))
                    .endResources()
                .endContainer()
                .addNewContainer().withName("c3")
                    .withNewResources()
                        .withRequests(Map.of("cpu", new Quantity("300m", ""),
                                             "memory", new Quantity("512Ki", "")))
                    .endResources()
                .endContainer()
                .addNewContainer().withName("c4")
                    .withNewResources()
                        .withRequests(Map.of("cpu", new Quantity("100m", ""),
                                             "memory", new Quantity("2048", "")))
                    .endResources()
                .endContainer()
                // container with no resources at all
                .addNewContainer().withName("c5").endContainer()
                // container with resources but no requests
                .addNewContainer().withName("c6").withNewResources().endResources().endContainer()
            .endSpec()
            .build();
    }

    @Test
    void getPodMetricsParsesContainerRequests() {
        stubPodGet("pod-a", richPod("pod-a"));

        ResourceMetrics metrics = service.getPodMetrics("pod-a");

        assertEquals("pod-a", metrics.getPodName());
        assertEquals(NS, metrics.getNamespace());
        // 500m + 2 cores + 300m + 100m = 500 + 2000 + 300 + 100 = 2900 millicores
        assertEquals(2900, metrics.getCpuRequestMillicores());
        assertEquals(2900, metrics.getCpuUsageMillicores());
        long expectedMem = 128L * 1024 * 1024 + 2L * 1024 * 1024 * 1024 + 512L * 1024 + 2048L;
        assertEquals(expectedMem, metrics.getMemoryRequestBytes());
    }

    @Test
    void getPodMetricsReturnsEmptyWhenPodMissing() {
        stubPodGet("missing", null);

        ResourceMetrics metrics = service.getPodMetrics("missing");

        assertEquals(0, metrics.getCpuRequestMillicores());
    }

    @Test
    void getPodMetricsReturnsEmptyOnException() {
        PodResource res = mock(PodResource.class);
        when(pods.withName(anyString())).thenReturn(res);
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertEquals(0, service.getPodMetrics("err").getCpuRequestMillicores());
    }

    @Test
    void getAllPodMetricsCollectsEachPod() {
        when(wrapper.listPods("")).thenReturn(List.of(
            PodInfo.builder().name("pod-a").build(),
            PodInfo.builder().name("pod-b").build()));
        stubPodGet("pod-a", richPod("pod-a"));
        stubPodGet("pod-b", richPod("pod-b"));

        Map<String, ResourceMetrics> all = service.getAllPodMetrics();

        assertEquals(2, all.size());
        assertTrue(all.containsKey("pod-a"));
    }

    @Test
    void getAllPodMetricsHandlesException() {
        when(wrapper.listPods("")).thenThrow(new RuntimeException("boom"));

        assertTrue(service.getAllPodMetrics().isEmpty());
    }

    @Test
    void getNamespaceResourceSummaryAggregatesTotals() {
        when(wrapper.listPods("")).thenReturn(List.of(PodInfo.builder().name("pod-a").build()));
        stubPodGet("pod-a", richPod("pod-a"));

        Map<String, ResourceMetrics> summary = service.getNamespaceResourceSummary();

        assertTrue(summary.containsKey("total"));
        assertTrue(summary.containsKey("pod-a"));
        assertEquals(2900, summary.get("total").getCpuUsageMillicores());
    }

    @Test
    void exceedsResourceThresholds() {
        stubPodGet("pod-a", richPod("pod-a"));

        // usage == request, so usage percentage is 100%
        assertTrue(service.exceedsResourceThresholds("pod-a", 50, 50));
        assertFalse(service.exceedsResourceThresholds("pod-a", 100, 100));
    }

    @Test
    void getPodsExceedingThresholdsFiltersByUsage() {
        when(wrapper.listPods("")).thenReturn(List.of(PodInfo.builder().name("pod-a").build()));
        stubPodGet("pod-a", richPod("pod-a"));

        assertTrue(service.getPodsExceedingThresholds(50, 50).contains("pod-a"));
        assertTrue(service.getPodsExceedingThresholds(100, 100).isEmpty());
    }

    @Test
    void getResourceQuotaReturnsHardAndUsed() {
        MixedOperation quotas = Fabric8Support.mixedOp();
        when(fabric8.resourceQuotas()).thenReturn(quotas);
        ResourceQuota quota = new ResourceQuotaBuilder()
            .withNewStatus()
                .withHard(Map.of("cpu", new Quantity("10", "")))
                .withUsed(Map.of("cpu", new Quantity("3", "")))
            .endStatus()
            .build();
        ResourceQuotaList list = new ResourceQuotaListBuilder().withItems(quota).build();
        when(quotas.list()).thenReturn(list);

        Map<String, String> result = service.getResourceQuota();

        assertEquals("10", result.get("hard.cpu"));
        assertEquals("3", result.get("used.cpu"));
    }

    @Test
    void getResourceQuotaReturnsEmptyWhenNoQuota() {
        MixedOperation quotas = Fabric8Support.mixedOp();
        when(fabric8.resourceQuotas()).thenReturn(quotas);
        when(quotas.list()).thenReturn(new ResourceQuotaListBuilder().build());

        assertTrue(service.getResourceQuota().isEmpty());
    }

    @Test
    void getResourceQuotaHandlesException() {
        MixedOperation quotas = Fabric8Support.mixedOp();
        when(fabric8.resourceQuotas()).thenReturn(quotas);
        when(quotas.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(service.getResourceQuota().isEmpty());
    }

    @Test
    void constructorFailsGracefullyInTestEnvironment() {
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
            () -> new ResourceMonitoringService(mock(KubernetesClient.class)));
    }
}
