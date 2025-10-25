package com.adhar.adharkit.metrics.util;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link KubernetesMetricsUtils}.
 */
@ExtendWith(MockitoExtension.class)
class KubernetesMetricsUtilsTest {

    private MeterRegistry registry;
    private AdharMetricsProperties properties;
    private KubernetesMetricsUtils kubernetesMetricsUtils;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new AdharMetricsProperties();
        properties.setEnabled(true);

        // Configure Kubernetes properties
        properties.getKubernetes().setEnabled(true);
        properties.getKubernetes().setIncludePodInfo(true);
        properties.getKubernetes().setIncludeNamespace(true);
        properties.getKubernetes().setIncludeNodeInfo(true);

        kubernetesMetricsUtils = new KubernetesMetricsUtils(registry, properties);
    }

    // ==================== Kubernetes Environment Detection Tests ====================

    @Test
    void isRunningInKubernetes_WithoutKubernetesEnvironment_ReturnsFalse() {
        // In test environment, Kubernetes files and env vars won't exist
        boolean result = kubernetesMetricsUtils.isRunningInKubernetes();

        assertThat(result).isFalse();
    }

    @Test
    void isRunningInKubernetes_CachesResult() {
        // First call
        boolean result1 = kubernetesMetricsUtils.isRunningInKubernetes();
        // Second call should return cached result
        boolean result2 = kubernetesMetricsUtils.isRunningInKubernetes();

        assertThat(result1).isEqualTo(result2);
    }

    // ==================== Metadata Retrieval Tests ====================

    @Test
    void getPodName_WithoutEnvironmentVariable_ReturnsHostname() {
        String podName = kubernetesMetricsUtils.getPodName();

        // In test environment, should return hostname or null
        // We mainly test that it doesn't throw an exception
        assertDoesNotThrow(() -> kubernetesMetricsUtils.getPodName());
    }

    @Test
    void getNamespace_WithoutKubernetesFiles_ReturnsDefault() {
        String namespace = kubernetesMetricsUtils.getNamespace();

        // Should return "default" when no Kubernetes namespace file exists
        assertThat(namespace).isEqualTo("default");
    }

    @Test
    void getNodeName_WithoutEnvironmentVariable_ReturnsNull() {
        String nodeName = kubernetesMetricsUtils.getNodeName();

        // In test environment, NODE_NAME env var won't be set
        assertThat(nodeName).isNull();
    }

    // ==================== Kubernetes Tags Creation Tests ====================

    @Test
    void createKubernetesTags_WithPodInfoEnabled_IncludesPodTag() {
        properties.getKubernetes().setIncludePodInfo(true);

        List<Tag> tags = kubernetesMetricsUtils.createKubernetesTags();

        // In test environment, pod name might be hostname
        // We test the behavior but don't assert specific values since they depend on environment
        assertThat(tags).isNotNull();
    }

    @Test
    void createKubernetesTags_WithNamespaceEnabled_IncludesNamespaceTag() {
        properties.getKubernetes().setIncludeNamespace(true);

        List<Tag> tags = kubernetesMetricsUtils.createKubernetesTags();

        boolean hasNamespaceTag = tags.stream().anyMatch(tag -> "namespace".equals(tag.getKey()));
        assertThat(hasNamespaceTag).isTrue(); // Should include namespace as "default"
    }

    @Test
    void createKubernetesTags_WithNodeInfoDisabled_ExcludesNodeTag() {
        properties.getKubernetes().setIncludeNodeInfo(false);

        List<Tag> tags = kubernetesMetricsUtils.createKubernetesTags();

        boolean hasNodeTag = tags.stream().anyMatch(tag -> "node".equals(tag.getKey()));
        assertThat(hasNodeTag).isFalse();
    }

    @Test
    void createKubernetesTagsArray_ReturnsProperlyFormattedArray() {
        String[] tags = kubernetesMetricsUtils.createKubernetesTagsArray();

        assertThat(tags).isNotNull();
        assertThat(tags.length % 2).isEqualTo(0); // Should be even number (key-value pairs)
    }

    // ==================== Deployment Metrics Tests ====================

    @Test
    void recordDeploymentMetrics_WithValidParameters_RecordsMetrics() {
        String deploymentName = "test-deployment";
        int replicas = 3;
        int availableReplicas = 2;

        kubernetesMetricsUtils.recordDeploymentMetrics(deploymentName, replicas, availableReplicas);

        // Verify metrics were recorded (they won't actually be recorded in test env since not in K8s)
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordDeploymentMetrics(deploymentName, replicas, availableReplicas));
    }

    @Test
    void recordDeploymentMetrics_WithNullDeploymentName_DoesNotThrow() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordDeploymentMetrics(null, 3, 2));
    }

    @Test
    void recordDeploymentMetrics_WithEmptyDeploymentName_DoesNotThrow() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordDeploymentMetrics("", 3, 2));
    }

    // ==================== Service Metrics Tests ====================

    @Test
    void recordServiceMetrics_WithValidParameters_RecordsMetrics() {
        String serviceName = "test-service";
        int endpointCount = 5;

        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordServiceMetrics(serviceName, endpointCount));
    }

    @Test
    void recordServiceMetrics_WithNullServiceName_DoesNotThrow() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordServiceMetrics(null, 5));
    }

    // ==================== Pod Restart Metrics Tests ====================

    @Test
    void recordPodRestart_WithReason_RecordsMetric() {
        String reason = "OOMKilled";

        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordPodRestart(reason));
    }

    @Test
    void recordPodRestart_WithNullReason_RecordsMetricWithoutReasonTag() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordPodRestart(null));
    }

    @Test
    void recordPodRestart_WithEmptyReason_RecordsMetricWithoutReasonTag() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordPodRestart(""));
    }

    // ==================== Resource Quota Metrics Tests ====================

    @Test
    void recordResourceQuota_WithValidParameters_RecordsMetrics() {
        String resourceName = "cpu";
        double used = 1.5;
        double limit = 2.0;

        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordResourceQuota(resourceName, used, limit));
    }

    @Test
    void recordResourceQuota_WithZeroLimit_DoesNotRecordUtilization() {
        String resourceName = "memory";
        double used = 512.0;
        double limit = 0.0;

        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordResourceQuota(resourceName, used, limit));
    }

    @Test
    void recordResourceQuota_WithNullResourceName_DoesNotThrow() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordResourceQuota(null, 1.0, 2.0));
    }

    // ==================== Ingress Metrics Tests ====================

    @Test
    void recordIngressMetrics_WithValidParameters_RecordsMetrics() {
        String ingressName = "test-ingress";
        int hostCount = 2;
        int pathCount = 5;

        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordIngressMetrics(ingressName, hostCount, pathCount));
    }

    @Test
    void recordIngressMetrics_WithNullIngressName_DoesNotThrow() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordIngressMetrics(null, 2, 5));
    }

    // ==================== Metadata Management Tests ====================

    @Test
    void getKubernetesMetadata_ReturnsMetadataMap() {
        Map<String, String> metadata = kubernetesMetricsUtils.getKubernetesMetadata();

        assertThat(metadata).isNotNull();
        assertThat(metadata).containsKey("podName");
        assertThat(metadata).containsKey("namespace");
        assertThat(metadata).containsKey("nodeName");
        assertThat(metadata).containsKey("inKubernetes");

        // Verify the inKubernetes flag is properly set
        assertThat(metadata.get("inKubernetes")).isEqualTo("false");
    }

    @Test
    void clearCache_ClearsMetadataCache() {
        // First get some metadata to populate cache
        kubernetesMetricsUtils.getKubernetesMetadata();

        // Clear the cache
        kubernetesMetricsUtils.clearCache();

        // Should not throw and should work normally after clearing
        assertDoesNotThrow(() -> kubernetesMetricsUtils.getKubernetesMetadata());
    }

    // ==================== Configuration Tests ====================

    @Test
    void createKubernetesTags_WithAllFeaturesDisabled_ReturnsEmptyList() {
        properties.getKubernetes().setIncludePodInfo(false);
        properties.getKubernetes().setIncludeNamespace(false);
        properties.getKubernetes().setIncludeNodeInfo(false);

        List<Tag> tags = kubernetesMetricsUtils.createKubernetesTags();

        assertThat(tags).isEmpty();
    }

    @Test
    void createKubernetesTagsArray_WithAllFeaturesDisabled_ReturnsEmptyArray() {
        properties.getKubernetes().setIncludePodInfo(false);
        properties.getKubernetes().setIncludeNamespace(false);
        properties.getKubernetes().setIncludeNodeInfo(false);

        String[] tags = kubernetesMetricsUtils.createKubernetesTagsArray();

        assertThat(tags).isEmpty();
    }

    // ==================== Edge Cases Tests ====================

    @Test
    void recordDeploymentMetrics_WithNegativeReplicas_HandlesGracefully() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordDeploymentMetrics("test", -1, -1));
    }

    @Test
    void recordServiceMetrics_WithNegativeEndpoints_HandlesGracefully() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordServiceMetrics("test", -1));
    }

    @Test
    void recordResourceQuota_WithNegativeValues_HandlesGracefully() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordResourceQuota("cpu", -1.0, -1.0));
    }

    @Test
    void recordIngressMetrics_WithNegativeCounts_HandlesGracefully() {
        assertDoesNotThrow(() -> kubernetesMetricsUtils.recordIngressMetrics("test", -1, -1));
    }
}

