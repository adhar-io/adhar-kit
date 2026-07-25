package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.annotation.KubernetesAutoScale;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.client.V2AutoscalingAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.AutoscalingAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HorizontalPodAutoscalerService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HorizontalPodAutoscalerServiceTest {

    private static final String NS = "test-ns";

    private HorizontalPodAutoscalerService service;
    private io.fabric8.kubernetes.client.KubernetesClient fabric8;
    private MixedOperation<HorizontalPodAutoscaler, ?, Resource<HorizontalPodAutoscaler>> hpas;

    @KubernetesAutoScale(minReplicas = 2, maxReplicas = 8, targetCpuUtilization = 65,
            targetMemoryUtilization = 75, scaleUpStabilization = 30, scaleDownStabilization = 180)
    static class Annotated {
    }

    @KubernetesAutoScale(targetMemoryUtilization = 0)
    static class NoMemoryTarget {
    }

    private KubernetesAutoScale annotationOf(Class<?> type) {
        for (Annotation a : type.getAnnotations()) {
            if (a instanceof KubernetesAutoScale scale) {
                return scale;
            }
        }
        throw new IllegalStateException("missing annotation");
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KubernetesClient wrapper = mock(KubernetesClient.class);
        when(wrapper.getNamespace()).thenReturn(NS);
        service = TestReflectionSupport.newInstanceWithoutConstructor(HorizontalPodAutoscalerService.class);
        TestReflectionSupport.setField(service, "kubernetesClient", wrapper);
        fabric8 = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);

        AutoscalingAPIGroupDSL autoscaling = mock(AutoscalingAPIGroupDSL.class);
        V2AutoscalingAPIGroupDSL v2 = mock(V2AutoscalingAPIGroupDSL.class);
        when(fabric8.autoscaling()).thenReturn(autoscaling);
        when(autoscaling.v2()).thenReturn(v2);
        hpas = Fabric8Support.mixedOp();
        when(v2.horizontalPodAutoscalers()).thenReturn((MixedOperation) hpas);
    }

    @Test
    void buildHorizontalPodAutoscalerProducesExpectedPayload() {
        HorizontalPodAutoscaler hpa = service.buildHorizontalPodAutoscaler("order-service", NS, annotationOf(Annotated.class));

        assertEquals("order-service", hpa.getMetadata().getName());
        assertEquals(NS, hpa.getMetadata().getNamespace());
        assertEquals("apps/v1", hpa.getSpec().getScaleTargetRef().getApiVersion());
        assertEquals("Deployment", hpa.getSpec().getScaleTargetRef().getKind());
        assertEquals("order-service", hpa.getSpec().getScaleTargetRef().getName());
        assertEquals(2, hpa.getSpec().getMinReplicas());
        assertEquals(8, hpa.getSpec().getMaxReplicas());
        assertEquals(30, hpa.getSpec().getBehavior().getScaleUp().getStabilizationWindowSeconds());
        assertEquals(180, hpa.getSpec().getBehavior().getScaleDown().getStabilizationWindowSeconds());

        assertEquals(2, hpa.getSpec().getMetrics().size());
        MetricSpec cpuMetric = hpa.getSpec().getMetrics().get(0);
        assertEquals("Resource", cpuMetric.getType());
        assertEquals("cpu", cpuMetric.getResource().getName());
        assertEquals(65, cpuMetric.getResource().getTarget().getAverageUtilization());
        assertEquals("Utilization", cpuMetric.getResource().getTarget().getType());

        MetricSpec memoryMetric = hpa.getSpec().getMetrics().get(1);
        assertEquals("memory", memoryMetric.getResource().getName());
        assertEquals(75, memoryMetric.getResource().getTarget().getAverageUtilization());
    }

    @Test
    void buildHorizontalPodAutoscalerOmitsMemoryMetricWhenTargetIsZero() {
        HorizontalPodAutoscaler hpa = service.buildHorizontalPodAutoscaler("order-service", NS,
                annotationOf(NoMemoryTarget.class));

        assertEquals(1, hpa.getSpec().getMetrics().size());
        assertEquals("cpu", hpa.getSpec().getMetrics().get(0).getResource().getName());
    }

    @Test
    void reconcileCreatesOrReplacesHpa() {
        Resource<HorizontalPodAutoscaler> resource = mock(Resource.class);
        when(hpas.resource(any(HorizontalPodAutoscaler.class))).thenReturn(resource);

        boolean result = service.reconcile("order-service", NS, annotationOf(Annotated.class));

        assertTrue(result);
        verify(resource).createOrReplace();
    }

    @Test
    void reconcileUsesWrappedClientNamespaceWhenNotSpecified() {
        Resource<HorizontalPodAutoscaler> resource = mock(Resource.class);
        when(hpas.resource(any(HorizontalPodAutoscaler.class))).thenReturn(resource);

        boolean result = service.reconcile("order-service", annotationOf(Annotated.class));

        assertTrue(result);
        verify(resource).createOrReplace();
    }

    @Test
    void reconcileReturnsFalseOnException() {
        when(hpas.resource(any(HorizontalPodAutoscaler.class))).thenThrow(new RuntimeException("boom"));

        assertFalse(service.reconcile("order-service", NS, annotationOf(Annotated.class)));
    }

    @Test
    void getHorizontalPodAutoscalerReturnsResource() {
        Resource<HorizontalPodAutoscaler> resource = mock(Resource.class);
        HorizontalPodAutoscaler expected = new HorizontalPodAutoscalerBuilder()
                .withNewMetadata().withName("order-service").endMetadata().build();
        when(hpas.withName("order-service")).thenReturn(resource);
        when(resource.get()).thenReturn(expected);

        assertEquals(expected, service.getHorizontalPodAutoscaler("order-service", NS));
    }

    @Test
    void getHorizontalPodAutoscalerReturnsNullOnException() {
        when(hpas.withName(anyString())).thenThrow(new RuntimeException("boom"));

        assertNull(service.getHorizontalPodAutoscaler("order-service", NS));
    }

    @Test
    void deleteReturnsTrueOnSuccess() {
        Resource<HorizontalPodAutoscaler> resource = mock(Resource.class);
        when(hpas.withName("order-service")).thenReturn(resource);

        assertTrue(service.delete("order-service", NS));
        verify(resource).delete();
    }

    @Test
    void deleteReturnsFalseOnException() {
        when(hpas.withName(anyString())).thenThrow(new RuntimeException("boom"));

        assertFalse(service.delete("order-service", NS));
    }

    @Test
    void constructorFailsGracefullyInTestEnvironment() {
        assertThrows(Throwable.class, () -> new HorizontalPodAutoscalerService(mock(KubernetesClient.class)));
    }
}
