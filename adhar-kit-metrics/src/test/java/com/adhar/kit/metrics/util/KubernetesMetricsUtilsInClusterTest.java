package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KubernetesMetricsUtils} that exercise the in-cluster code paths.
 * <p>
 * Outside Kubernetes the {@code record*} methods short-circuit, so this suite forces
 * the detection flag to {@code true} and pre-populates the metadata cache (via
 * reflection) to drive the actual metric-emitting branches.
 * </p>
 */
class KubernetesMetricsUtilsInClusterTest {

    private SimpleMeterRegistry registry;
    private AdharMetricsProperties properties;
    private KubernetesMetricsUtils utils;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        registry = new SimpleMeterRegistry();
        properties = new AdharMetricsProperties();
        properties.setEnabled(true);
        properties.getKubernetes().setEnabled(true);
        properties.getKubernetes().setIncludePodInfo(true);
        properties.getKubernetes().setIncludeNamespace(true);
        properties.getKubernetes().setIncludeNodeInfo(true);

        utils = new KubernetesMetricsUtils(registry, properties);

        // Force "running in Kubernetes" so the record* methods do not short-circuit.
        Field flag = KubernetesMetricsUtils.class.getDeclaredField("isInKubernetes");
        flag.setAccessible(true);
        ((AtomicReference<Boolean>) flag.get(utils)).set(Boolean.TRUE);

        // Pre-populate metadata so all three K8s tags are produced.
        Field cacheField = KubernetesMetricsUtils.class.getDeclaredField("metadataCache");
        cacheField.setAccessible(true);
        Map<String, String> cache = (Map<String, String>) cacheField.get(utils);
        cache.put("podName", "pod-1");
        cache.put("namespace", "team-a");
        cache.put("nodeName", "node-7");
    }

    @Test
    void createKubernetesTags_withAllMetadata_includesPodNamespaceNode() {
        List<Tag> tags = utils.createKubernetesTags();

        assertThat(tags).extracting(Tag::getKey)
                .containsExactlyInAnyOrder("pod", "namespace", "node");
        assertThat(utils.createKubernetesTagsArray()).hasSize(6);
    }

    @Test
    void recordDeploymentMetrics_registersReplicaGauges() {
        utils.recordDeploymentMetrics("orders", 3, 2);

        assertThat(registry.find("kubernetes.deployment.replicas").gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("kubernetes.deployment.available_replicas").gauge().value()).isEqualTo(2.0);
        assertThat(registry.find("kubernetes.deployment.unavailable_replicas").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void recordServiceMetrics_registersEndpointGauge() {
        utils.recordServiceMetrics("orders-svc", 5);

        assertThat(registry.find("kubernetes.service.endpoints").gauge().value()).isEqualTo(5.0);
    }

    @Test
    void recordPodRestart_withReason_incrementsCounter() {
        utils.recordPodRestart("OOMKilled");

        assertThat(registry.find("kubernetes.pod.restarts").tag("reason", "OOMKilled")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordPodRestart_withoutReason_incrementsCounter() {
        utils.recordPodRestart(null);

        assertThat(registry.find("kubernetes.pod.restarts").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordResourceQuota_withPositiveLimit_recordsUtilization() {
        utils.recordResourceQuota("cpu", 1.5, 2.0);

        assertThat(registry.find("kubernetes.resource.used").gauge().value()).isEqualTo(1.5);
        assertThat(registry.find("kubernetes.resource.limit").gauge().value()).isEqualTo(2.0);
        assertThat(registry.find("kubernetes.resource.utilization_percent").gauge().value()).isEqualTo(75.0);
    }

    @Test
    void recordResourceQuota_withZeroLimit_skipsUtilization() {
        utils.recordResourceQuota("memory", 512.0, 0.0);

        assertThat(registry.find("kubernetes.resource.used").gauge().value()).isEqualTo(512.0);
        assertThat(registry.find("kubernetes.resource.utilization_percent").gauge()).isNull();
    }

    @Test
    void recordIngressMetrics_registersHostAndPathGauges() {
        utils.recordIngressMetrics("web", 2, 5);

        assertThat(registry.find("kubernetes.ingress.hosts").gauge().value()).isEqualTo(2.0);
        assertThat(registry.find("kubernetes.ingress.paths").gauge().value()).isEqualTo(5.0);
    }

    @Test
    void getKubernetesMetadata_reportsInClusterTrue() {
        Map<String, String> metadata = utils.getKubernetesMetadata();

        assertThat(metadata.get("inKubernetes")).isEqualTo("true");
        assertThat(metadata.get("podName")).isEqualTo("pod-1");
        assertThat(metadata.get("namespace")).isEqualTo("team-a");
        assertThat(metadata.get("nodeName")).isEqualTo("node-7");
    }
}
