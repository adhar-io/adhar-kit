package com.adhar.kit.kubernetes;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.DeploymentInfo;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import com.adhar.kit.kubernetes.service.DeploymentService;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KubernetesFacade}.
 *
 * <p>The dependency-injection constructor is used to exercise delegation, and a
 * facade with no client (allocated without a constructor) exercises the
 * not-available guard branches.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KubernetesFacadeTest {

    private KubernetesClient client;
    private DeploymentService deploymentService;
    private KubernetesFacade facade;
    private KubernetesFacade nullFacade;

    @BeforeEach
    void setUp() {
        client = org.mockito.Mockito.mock(KubernetesClient.class);
        deploymentService = org.mockito.Mockito.mock(DeploymentService.class);
        facade = new KubernetesFacade(client, deploymentService);

        // A facade whose client/deploymentService/properties are all null so the
        // "not available" guard branches are taken without trying to build a client.
        nullFacade = TestReflectionSupport.newInstanceWithoutConstructor(KubernetesFacade.class);
    }

    // ----- delegation paths -----

    @Test
    void getConfigMapValueReturnsKey() {
        when(client.getConfigMap("cfg")).thenReturn(Map.of("k", "v"));

        assertEquals("v", facade.getConfigMapValue("cfg", "k"));
        assertNull(facade.getConfigMapValue("cfg", "missing"));
    }

    @Test
    void getConfigMapValueHandlesNullData() {
        when(client.getConfigMap("cfg")).thenReturn(null);
        assertNull(facade.getConfigMapValue("cfg", "k"));
    }

    @Test
    void getConfigMapDataReturnsMapOrEmpty() {
        when(client.getConfigMap("cfg")).thenReturn(Map.of("k", "v"));
        assertEquals(1, facade.getConfigMapData("cfg").size());

        when(client.getConfigMap("cfg2")).thenReturn(null);
        assertTrue(facade.getConfigMapData("cfg2").isEmpty());
    }

    @Test
    void getSecretValueReturnsKey() {
        when(client.getSecret("sec")).thenReturn(Map.of("password", "p"));

        assertEquals("p", facade.getSecretValue("sec", "password"));
        assertNull(facade.getSecretValue("sec", "missing"));
    }

    @Test
    void getSecretValueHandlesNullData() {
        when(client.getSecret("sec")).thenReturn(null);
        assertNull(facade.getSecretValue("sec", "password"));
    }

    @Test
    void getSecretDataReturnsMapOrEmpty() {
        when(client.getSecret("sec")).thenReturn(Map.of("password", "p"));
        assertEquals(1, facade.getSecretData("sec").size());

        when(client.getSecret("sec2")).thenReturn(null);
        assertTrue(facade.getSecretData("sec2").isEmpty());
    }

    @Test
    void scaleDeploymentDelegates() {
        facade.scaleDeployment("order", 4);
        verify(deploymentService).scaleDeployment("order", 4);
    }

    @Test
    void getCurrentPodInfoBuildsMapWithoutNulls() {
        PodInfo podInfo = PodInfo.builder()
            .name("pod-a").namespace("ns").ip("1.1.1.1").phase("Running").build();
        when(client.getCurrentPodInfo()).thenReturn(podInfo);

        Map<String, Object> map = facade.getCurrentPodInfo();

        assertEquals("pod-a", map.get("name"));
        assertEquals("ns", map.get("namespace"));
        assertEquals(true, map.get("isRunning"));
        assertFalse(map.containsKey("nodeName")); // null fields removed
    }

    @Test
    void getCurrentPodInfoReturnsEmptyWhenPodInfoNull() {
        when(client.getCurrentPodInfo()).thenReturn(null);
        assertTrue(facade.getCurrentPodInfo().isEmpty());
    }

    @Test
    void getCurrentNamespaceDelegates() {
        when(client.getNamespace()).thenReturn("prod");
        assertEquals("prod", facade.getCurrentNamespace());
    }

    @Test
    void isInKubernetesReflectsEnvironment() {
        // KUBERNETES_SERVICE_HOST is provided via Surefire.
        assertTrue(facade.isInKubernetes());
    }

    @Test
    void listPodsReturnsNamesFilteringNulls() {
        when(client.listPods("app=order")).thenReturn(List.of(
            PodInfo.builder().name("p1").build(),
            PodInfo.builder().build(),
            PodInfo.builder().name("p2").build()));

        List<String> names = facade.listPods("app=order");

        assertEquals(List.of("p1", "p2"), names);
    }

    @Test
    void getDeploymentInfoDelegates() {
        DeploymentInfo info = DeploymentInfo.builder().name("order").build();
        when(deploymentService.getDeployment("order")).thenReturn(info);

        assertSame(info, facade.getDeploymentInfo("order"));
    }

    @Test
    void listServicesDelegates() {
        ServiceInfo svc = ServiceInfo.builder().name("svc").build();
        when(client.discoverServices("app=order")).thenReturn(List.of(svc));

        assertEquals(1, facade.listServices("app=order").size());
    }

    @Test
    void createOrUpdateConfigMapDelegates() {
        facade.createOrUpdateConfigMap("cfg", Map.of("a", "b"));
        verify(client).createOrUpdateConfigMap("cfg", Map.of("a", "b"));
    }

    @Test
    void restartDeploymentDelegates() {
        when(deploymentService.restartDeployment("order")).thenReturn(true);
        assertTrue(facade.restartDeployment("order"));
    }

    // ----- not-available guard paths -----

    @Test
    void guardsReturnSafeDefaultsWhenClientUnavailable() {
        assertNull(nullFacade.getConfigMapValue("cfg", "k"));
        assertTrue(nullFacade.getConfigMapData("cfg").isEmpty());
        assertNull(nullFacade.getSecretValue("sec", "k"));
        assertTrue(nullFacade.getSecretData("sec").isEmpty());
        assertTrue(nullFacade.getCurrentPodInfo().isEmpty());
        assertEquals("default", nullFacade.getCurrentNamespace());
        assertTrue(nullFacade.listPods("app=x").isEmpty());
        assertTrue(nullFacade.listServices("app=x").isEmpty());
        assertNull(nullFacade.getDeploymentInfo("order"));
        assertFalse(nullFacade.restartDeployment("order"));
        // void methods must not throw
        nullFacade.scaleDeployment("order", 2);
        nullFacade.createOrUpdateConfigMap("cfg", Map.of());
    }

    @Test
    void getInstanceReturnsSingleton() {
        KubernetesFacade a = KubernetesFacade.getInstance();
        KubernetesFacade b = KubernetesFacade.getInstance();
        assertSame(a, b);
    }
}
