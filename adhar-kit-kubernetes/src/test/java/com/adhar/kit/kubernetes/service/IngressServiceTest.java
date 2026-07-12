package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.Fabric8Support;
import com.adhar.kit.kubernetes.TestReflectionSupport;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.IngressInfo;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder;
import io.fabric8.kubernetes.client.dsl.NetworkAPIGroupDSL;
import io.fabric8.kubernetes.client.V1NetworkAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IngressService} backed by a mocked Fabric8 client.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngressServiceTest {

    private static final String NS = "test-ns";

    private IngressService service;
    private MixedOperation ingresses;

    @BeforeEach
    void setUp() {
        KubernetesClient wrapper = mock(KubernetesClient.class);
        when(wrapper.getNamespace()).thenReturn(NS);
        service = TestReflectionSupport.newInstanceWithoutConstructor(IngressService.class);
        TestReflectionSupport.setField(service, "kubernetesClient", wrapper);

        io.fabric8.kubernetes.client.KubernetesClient fabric8 =
            mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", fabric8);

        NetworkAPIGroupDSL network = mock(NetworkAPIGroupDSL.class);
        V1NetworkAPIGroupDSL v1 = mock(V1NetworkAPIGroupDSL.class);
        ingresses = Fabric8Support.mixedOp();
        when(fabric8.network()).thenReturn(network);
        when(network.v1()).thenReturn(v1);
        lenient().when(v1.ingresses()).thenReturn(ingresses);
    }

    private Resource ingressResource() {
        Resource res = mock(Resource.class);
        when(ingresses.withName(anyString())).thenReturn(res);
        return res;
    }

    private Ingress sampleIngress(String name, String host, String tlsHost, String lbIp) {
        IngressBuilder builder = new IngressBuilder()
            .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
            .withNewSpec()
                .addNewRule().withHost(host).endRule()
                .addNewTl().withHosts(tlsHost).endTl()
            .endSpec();
        if (lbIp != null) {
            builder.withNewStatus()
                .withNewLoadBalancer()
                    .addNewIngress().withIp(lbIp).endIngress()
                .endLoadBalancer()
                .endStatus();
        }
        return builder.build();
    }

    @Test
    void getIngressMapsFields() {
        Resource res = ingressResource();
        when(res.get()).thenReturn(sampleIngress("web", "example.com", "secure.com", "1.2.3.4"));

        IngressInfo info = service.getIngress("web");

        assertEquals("web", info.getName());
        assertTrue(info.getHosts().contains("example.com"));
        assertTrue(info.getTlsHosts().contains("secure.com"));
        assertEquals("1.2.3.4", info.getLoadBalancerIp());
        assertTrue(info.hasLoadBalancer());
        assertTrue(info.hasTLS());
    }

    @Test
    void getIngressReturnsNullWhenMissing() {
        Resource res = ingressResource();
        when(res.get()).thenReturn(null);

        assertNull(service.getIngress("missing"));
    }

    @Test
    void getIngressReturnsNullOnException() {
        Resource res = ingressResource();
        when(res.get()).thenThrow(new RuntimeException("boom"));

        assertNull(service.getIngress("err"));
    }

    @Test
    void listIngressMapsItems() {
        IngressList list = new IngressListBuilder()
            .withItems(sampleIngress("web", "example.com", "secure.com", "1.2.3.4"))
            .build();
        when(ingresses.list()).thenReturn(list);

        List<IngressInfo> infos = service.listIngress();

        assertEquals(1, infos.size());
        assertEquals("web", infos.get(0).getName());
    }

    @Test
    void listIngressReturnsEmptyOnException() {
        when(ingresses.list()).thenThrow(new RuntimeException("boom"));

        assertTrue(service.listIngress().isEmpty());
    }

    @Test
    void getIngressByHostFindsMatch() {
        IngressList list = new IngressListBuilder()
            .withItems(sampleIngress("web", "example.com", "secure.com", "1.2.3.4"))
            .build();
        when(ingresses.list()).thenReturn(list);

        assertEquals("web", service.getIngressByHost("example.com").getName());
        assertNull(service.getIngressByHost("nomatch.com"));
    }

    @Test
    void hasLoadBalancerIpReflectsStatus() {
        Resource res = ingressResource();
        when(res.get()).thenReturn(sampleIngress("web", "example.com", "secure.com", "1.2.3.4"));

        assertTrue(service.hasLoadBalancerIP("web"));
        assertEquals("1.2.3.4", service.getLoadBalancerIP("web"));
    }

    @Test
    void hasLoadBalancerIpFalseWhenNoIp() {
        Resource res = ingressResource();
        when(res.get()).thenReturn(sampleIngress("web", "example.com", "secure.com", null));

        assertFalse(service.hasLoadBalancerIP("web"));
        assertNull(service.getLoadBalancerIP("web"));
    }

    @Test
    void loadBalancerHelpersHandleMissingIngress() {
        Resource res = ingressResource();
        when(res.get()).thenReturn(null);

        assertFalse(service.hasLoadBalancerIP("missing"));
        assertNull(service.getLoadBalancerIP("missing"));
    }

    @Test
    void constructorFailsGracefullyInTestEnvironment() {
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
            () -> new IngressService(mock(KubernetesClient.class)));
    }
}
