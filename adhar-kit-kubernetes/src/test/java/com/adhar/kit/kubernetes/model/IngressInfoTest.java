package com.adhar.kit.kubernetes.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the hand-written logic of {@link IngressInfo}.
 */
class IngressInfoTest {

    @Test
    void hasLoadBalancerTrueWhenIpPresent() {
        IngressInfo ingress = IngressInfo.builder().loadBalancerIp("1.2.3.4").build();
        assertTrue(ingress.hasLoadBalancer());
    }

    @Test
    void hasLoadBalancerFalseWhenNullOrEmpty() {
        assertFalse(IngressInfo.builder().build().hasLoadBalancer());
        assertFalse(IngressInfo.builder().loadBalancerIp("").build().hasLoadBalancer());
    }

    @Test
    void hasTlsTrueWhenTlsHostsPresent() {
        IngressInfo ingress = IngressInfo.builder().tlsHosts(List.of("secure.com")).build();
        assertTrue(ingress.hasTLS());
    }

    @Test
    void hasTlsFalseWhenEmpty() {
        assertFalse(IngressInfo.builder().tlsHosts(List.of()).build().hasTLS());
    }
}
