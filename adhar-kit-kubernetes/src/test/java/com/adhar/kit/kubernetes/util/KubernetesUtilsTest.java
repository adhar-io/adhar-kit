package com.adhar.kit.kubernetes.util;

import com.adhar.kit.kubernetes.model.PodInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KubernetesUtils.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class KubernetesUtilsTest {

    @Test
    void testParseLabelSelector() {
        Map<String, String> labels = KubernetesUtils.parseLabelSelector("app=order,env=prod");

        assertEquals(2, labels.size());
        assertEquals("order", labels.get("app"));
        assertEquals("prod", labels.get("env"));
    }

    @Test
    void testParseLabelSelector_Empty() {
        Map<String, String> labels = KubernetesUtils.parseLabelSelector("");
        assertTrue(labels.isEmpty());

        labels = KubernetesUtils.parseLabelSelector(null);
        assertTrue(labels.isEmpty());
    }

    @Test
    void testIsValidResourceName_Valid() {
        assertTrue(KubernetesUtils.isValidResourceName("my-service"));
        assertTrue(KubernetesUtils.isValidResourceName("order-service-v1"));
        assertTrue(KubernetesUtils.isValidResourceName("abc123"));
    }

    @Test
    void testIsValidResourceName_Invalid() {
        assertFalse(KubernetesUtils.isValidResourceName(null));
        assertFalse(KubernetesUtils.isValidResourceName(""));
        assertFalse(KubernetesUtils.isValidResourceName("My-Service")); // uppercase
        assertFalse(KubernetesUtils.isValidResourceName("-service")); // starts with -
        assertFalse(KubernetesUtils.isValidResourceName("service-")); // ends with -
        assertFalse(KubernetesUtils.isValidResourceName("my_service")); // underscore
    }

    @Test
    void testCreateLabelSelector() {
        Map<String, String> labels = Map.of(
            "app", "order",
            "env", "prod"
        );

        String selector = KubernetesUtils.createLabelSelector(labels);

        assertTrue(selector.contains("app=order"));
        assertTrue(selector.contains("env=prod"));
        assertTrue(selector.contains(","));
    }

    @Test
    void testCreateLabelSelector_Empty() {
        String selector = KubernetesUtils.createLabelSelector(Map.of());
        assertEquals("", selector);

        selector = KubernetesUtils.createLabelSelector(null);
        assertEquals("", selector);
    }

    @Test
    void testSanitizeResourceName() {
        assertEquals("my-service", KubernetesUtils.sanitizeResourceName("My_Service"));
        assertEquals("order-service", KubernetesUtils.sanitizeResourceName("Order@Service"));
        assertEquals("xabc", KubernetesUtils.sanitizeResourceName("abc-"));
        assertEquals("xabcx", KubernetesUtils.sanitizeResourceName("-abc-"));
    }

    @Test
    void testSanitizeResourceName_Long() {
        String longName = "a".repeat(300);
        String sanitized = KubernetesUtils.sanitizeResourceName(longName);

        assertEquals(253, sanitized.length());
        assertTrue(KubernetesUtils.isValidResourceName(sanitized));
    }

    @Test
    void testIsPodReady() {
        PodInfo readyPod = PodInfo.builder()
            .phase("Running")
            .build();

        assertTrue(KubernetesUtils.isPodReady(readyPod));

        PodInfo pendingPod = PodInfo.builder()
            .phase("Pending")
            .build();

        assertFalse(KubernetesUtils.isPodReady(pendingPod));
        assertFalse(KubernetesUtils.isPodReady(null));
    }

    @Test
    void testGetServiceAccountTokenPath() {
        String path = KubernetesUtils.getServiceAccountTokenPath();
        assertEquals("/var/run/secrets/kubernetes.io/serviceaccount/token", path);
    }

    @Test
    void testGetServiceAccountCACertPath() {
        String path = KubernetesUtils.getServiceAccountCACertPath();
        assertEquals("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt", path);
    }
}

