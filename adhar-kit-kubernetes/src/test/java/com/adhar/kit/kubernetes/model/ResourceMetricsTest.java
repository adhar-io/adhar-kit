package com.adhar.kit.kubernetes.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceMetrics model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class ResourceMetricsTest {

    @Test
    void testResourceMetricsBuilder() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .podName("my-pod")
            .namespace("default")
            .cpuUsageMillicores(500)
            .cpuRequestMillicores(1000)
            .memoryUsageBytes(512 * 1024 * 1024)
            .memoryRequestBytes(1024 * 1024 * 1024)
            .build();

        assertEquals("my-pod", metrics.getPodName());
        assertEquals("default", metrics.getNamespace());
        assertEquals(500, metrics.getCpuUsageMillicores());
        assertEquals(512 * 1024 * 1024, metrics.getMemoryUsageBytes());
    }

    @Test
    void testGetCpuUsagePercentage() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .cpuUsageMillicores(750)
            .cpuRequestMillicores(1000)
            .build();

        assertEquals(75, metrics.getCpuUsagePercentage());
    }

    @Test
    void testGetMemoryUsagePercentage() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .memoryUsageBytes(800 * 1024 * 1024)
            .memoryRequestBytes(1024 * 1024 * 1024)
            .build();

        assertEquals(78, metrics.getMemoryUsagePercentage());
    }

    @Test
    void testGetCpuUsageCores() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .cpuUsageMillicores(1500)
            .build();

        assertEquals(1.5, metrics.getCpuUsageCores(), 0.01);
    }

    @Test
    void testGetMemoryUsageMB() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .memoryUsageBytes(512 * 1024 * 1024)
            .build();

        assertEquals(512, metrics.getMemoryUsageMB());
    }

    @Test
    void testGetMemoryUsageGB() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .memoryUsageBytes(2L * 1024 * 1024 * 1024)
            .build();

        assertEquals(2.0, metrics.getMemoryUsageGB(), 0.01);
    }

    @Test
    void testIsHighCpuUsage() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .cpuUsageMillicores(850)
            .cpuRequestMillicores(1000)
            .build();

        assertTrue(metrics.isHighCpuUsage(80));
        assertFalse(metrics.isHighCpuUsage(90));
    }

    @Test
    void testIsHighMemoryUsage() {
        ResourceMetrics metrics = ResourceMetrics.builder()
            .memoryUsageBytes(900 * 1024 * 1024)
            .memoryRequestBytes(1024 * 1024 * 1024)
            .build();

        assertTrue(metrics.isHighMemoryUsage(80));
        assertFalse(metrics.isHighMemoryUsage(95));
    }
}

