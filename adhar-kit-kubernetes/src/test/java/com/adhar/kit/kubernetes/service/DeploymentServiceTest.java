package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.DeploymentInfo;
import com.adhar.kit.kubernetes.model.ReplicaSetInfo;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetListBuilder;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.TimeoutImageEditReplacePatchable;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeploymentService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentServiceTest {

    private static final String NS = "test-ns";

    private DeploymentService service;
    private io.fabric8.kubernetes.client.KubernetesClient fabric8;
    private AppsAPIGroupDSL apps;
    private MixedOperation deployments;
    private MixedOperation replicaSets;

    @BeforeEach
    void setUp() {
        KubernetesClient wrapper = mock(KubernetesClient.class);
        when(wrapper.getNamespace()).thenReturn(NS);
        service = TestReflectionSupport.newInstanceWithoutConstructor(DeploymentService.class);
        TestReflectionSupport.setField(service, "kubernetesClient", wrapper);
        fabric8 = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);

        apps = mock(AppsAPIGroupDSL.class);
        when(fabric8.apps()).thenReturn(apps);
        deployments = Fabric8Support.mixedOp();
        replicaSets = Fabric8Support.mixedOp();
        lenient().when(apps.deployments()).thenReturn(deployments);
        lenient().when(apps.replicaSets()).thenReturn(replicaSets);
    }

    private RollableScalableResource deploymentResource() {
        RollableScalableResource res = mock(RollableScalableResource.class);
        when(deployments.withName(anyString())).thenReturn(res);
        return res;
    }

    private Deployment sampleDeployment(String name, int replicas, int ready) {
        return new DeploymentBuilder()
            .withNewMetadata().withName(name).withNamespace(NS)
                .withLabels(Map.of("app", name)).endMetadata()
            .withNewSpec()
                .withReplicas(replicas)
                .withPaused(false)
                .withNewSelector().withMatchLabels(Map.of("app", name)).endSelector()
            .endSpec()
            .withNewStatus()
                .withReadyReplicas(ready)
                .withAvailableReplicas(ready)
                .withUnavailableReplicas(replicas - ready)
            .endStatus()
            .build();
    }

    @Test
    void getDeploymentMapsFields() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenReturn(sampleDeployment("order", 3, 3));

        DeploymentInfo info = service.getDeployment("order");

        assertNotNull(info);
        assertEquals("order", info.getName());
        assertEquals(NS, info.getNamespace());
        assertEquals(3, info.getReplicas());
        assertEquals(3, info.getReadyReplicas());
        assertTrue(info.isReady());
    }

    @Test
    void getDeploymentReturnsNullWhenMissing() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenReturn(null);

        assertNull(service.getDeployment("missing"));
    }

    @Test
    void getDeploymentReturnsNullOnException() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertNull(service.getDeployment("err"));
    }

    @Test
    void listDeploymentsWithoutSelector() {
        DeploymentList list = new DeploymentListBuilder()
            .withItems(sampleDeployment("a", 2, 2), sampleDeployment("b", 1, 0))
            .build();
        when(deployments.list()).thenReturn(list);

        List<DeploymentInfo> infos = service.listDeployments();

        assertEquals(2, infos.size());
    }

    @Test
    void listDeploymentsWithSelector() {
        DeploymentList list = new DeploymentListBuilder()
            .withItems(sampleDeployment("a", 2, 2))
            .build();
        when(deployments.list()).thenReturn(list);

        List<DeploymentInfo> infos = service.listDeployments("app=a");

        assertEquals(1, infos.size());
    }

    @Test
    void listDeploymentsReturnsEmptyOnException() {
        when(deployments.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(service.listDeployments("app=a").isEmpty());
    }

    @Test
    void scaleDeploymentReturnsTrueOnSuccess() {
        RollableScalableResource res = deploymentResource();
        when(res.scale(anyInt())).thenReturn(sampleDeployment("order", 5, 5));

        assertTrue(service.scaleDeployment("order", 5));
        verify(res).scale(5);
    }

    @Test
    void scaleDeploymentReturnsFalseOnException() {
        RollableScalableResource res = deploymentResource();
        when(res.scale(anyInt())).thenThrow(new RuntimeException("boom"));

        assertFalse(service.scaleDeployment("order", 5));
    }

    @Test
    void restartPauseResumeRollbackAndUpdateImage() {
        RollableScalableResource res = deploymentResource();
        TimeoutImageEditReplacePatchable rolling = mock(TimeoutImageEditReplacePatchable.class);
        when(res.rolling()).thenReturn(rolling);

        assertTrue(service.restartDeployment("order"));
        assertTrue(service.pauseDeployment("order"));
        assertTrue(service.resumeDeployment("order"));
        assertTrue(service.rollbackDeployment("order"));
        assertTrue(service.updateImage("order", "app", "repo/app:2"));

        verify(rolling).restart();
        verify(rolling).pause();
        verify(rolling).resume();
        verify(rolling).undo();
        verify(rolling).updateImage(Map.of("app", "repo/app:2"));
    }

    @Test
    void rollingOperationsReturnFalseOnException() {
        RollableScalableResource res = deploymentResource();
        when(res.rolling()).thenThrow(new RuntimeException("boom"));

        assertFalse(service.restartDeployment("order"));
        assertFalse(service.pauseDeployment("order"));
        assertFalse(service.resumeDeployment("order"));
        assertFalse(service.rollbackDeployment("order"));
        assertFalse(service.updateImage("order", "app", "img"));
    }

    @Test
    void isDeploymentReadyTrueWhenAllReplicasReady() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenReturn(sampleDeployment("order", 3, 3));

        assertTrue(service.isDeploymentReady("order"));
    }

    @Test
    void isDeploymentReadyFalseWhenNotReady() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenReturn(sampleDeployment("order", 3, 1));

        assertFalse(service.isDeploymentReady("order"));
    }

    @Test
    void isDeploymentReadyFalseWhenMissing() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenReturn(null);

        assertFalse(service.isDeploymentReady("missing"));
    }

    @Test
    void isDeploymentReadyFalseOnException() {
        RollableScalableResource res = deploymentResource();
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertFalse(service.isDeploymentReady("err"));
    }

    @Test
    void getReplicaSetsMapsItems() {
        ReplicaSet rs = new ReplicaSetBuilder()
            .withNewMetadata().withName("rs-1").withNamespace(NS)
                .withLabels(Map.of("app", "order")).endMetadata()
            .withNewSpec().withReplicas(2).endSpec()
            .withNewStatus().withReadyReplicas(2).endStatus()
            .build();
        ReplicaSetList list = new ReplicaSetListBuilder().withItems(rs).build();
        when(replicaSets.list()).thenReturn(list);

        List<ReplicaSetInfo> infos = service.getReplicaSets("order");

        assertEquals(1, infos.size());
        assertEquals("rs-1", infos.get(0).getName());
        assertEquals(2, infos.get(0).getReplicas());
    }

    @Test
    void getReplicaSetsReturnsEmptyOnException() {
        when(replicaSets.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(service.getReplicaSets("order").isEmpty());
    }

    @Test
    void constructorFailsGracefullyInTestEnvironment() {
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
            () -> new DeploymentService(mock(KubernetesClient.class)));
    }
}
