package com.adhar.kit.kubernetes.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PodInfo model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class PodInfoTest {

    @Test
    void testPodInfoBuilder() {
        PodInfo podInfo = PodInfo.builder()
            .name("my-pod")
            .namespace("default")
            .ip("10.0.0.1")
            .nodeName("node-1")
            .phase("Running")
            .serviceAccount("default")
            .build();

        assertEquals("my-pod", podInfo.getName());
        assertEquals("default", podInfo.getNamespace());
        assertEquals("10.0.0.1", podInfo.getIp());
        assertEquals("node-1", podInfo.getNodeName());
        assertEquals("Running", podInfo.getPhase());
        assertEquals("default", podInfo.getServiceAccount());
    }

    @Test
    void testIsRunning_True() {
        PodInfo podInfo = PodInfo.builder()
            .phase("Running")
            .build();

        assertTrue(podInfo.isRunning());
    }

    @Test
    void testIsRunning_False() {
        PodInfo podInfo = PodInfo.builder()
            .phase("Pending")
            .build();

        assertFalse(podInfo.isRunning());

        podInfo = PodInfo.builder()
            .phase("Failed")
            .build();

        assertFalse(podInfo.isRunning());
    }

    @Test
    void testLabelsAndAnnotations() {
        Map<String, String> labels = Map.of("app", "order", "env", "prod");
        Map<String, String> annotations = Map.of("version", "1.0.0");

        PodInfo podInfo = PodInfo.builder()
            .labels(labels)
            .annotations(annotations)
            .build();

        assertEquals(2, podInfo.getLabels().size());
        assertEquals("order", podInfo.getLabels().get("app"));
        assertEquals(1, podInfo.getAnnotations().size());
        assertEquals("1.0.0", podInfo.getAnnotations().get("version"));
    }
}

