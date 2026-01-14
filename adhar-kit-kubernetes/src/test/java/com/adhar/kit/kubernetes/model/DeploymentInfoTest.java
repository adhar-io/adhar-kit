package com.adhar.kit.kubernetes.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeploymentInfo model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class DeploymentInfoTest {

    @Test
    void testDeploymentInfoBuilder() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .name("order-service")
            .namespace("production")
            .replicas(3)
            .readyReplicas(3)
            .availableReplicas(3)
            .build();

        assertEquals("order-service", deployment.getName());
        assertEquals("production", deployment.getNamespace());
        assertEquals(3, deployment.getReplicas());
        assertEquals(3, deployment.getReadyReplicas());
    }

    @Test
    void testIsReady_AllReady() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .replicas(3)
            .readyReplicas(3)
            .build();

        assertTrue(deployment.isReady());
    }

    @Test
    void testIsReady_NotAllReady() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .replicas(3)
            .readyReplicas(2)
            .build();

        assertFalse(deployment.isReady());
    }

    @Test
    void testGetHealthPercentage() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .replicas(10)
            .readyReplicas(7)
            .build();

        assertEquals(70, deployment.getHealthPercentage());
    }

    @Test
    void testGetHealthPercentage_AllReady() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .replicas(5)
            .readyReplicas(5)
            .build();

        assertEquals(100, deployment.getHealthPercentage());
    }

    @Test
    void testGetHealthPercentage_NoReplicas() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .replicas(0)
            .readyReplicas(0)
            .build();

        assertEquals(0, deployment.getHealthPercentage());
    }

    @Test
    void testLabelsAndSelector() {
        Map<String, String> labels = Map.of("app", "order", "env", "prod");
        Map<String, String> selector = Map.of("app", "order");

        DeploymentInfo deployment = DeploymentInfo.builder()
            .labels(labels)
            .selector(selector)
            .build();

        assertEquals(2, deployment.getLabels().size());
        assertEquals(1, deployment.getSelector().size());
    }

    @Test
    void testPaused() {
        DeploymentInfo deployment = DeploymentInfo.builder()
            .paused(true)
            .build();

        assertTrue(deployment.isPaused());
    }
}

