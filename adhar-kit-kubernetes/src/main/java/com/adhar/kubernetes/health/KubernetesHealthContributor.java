package com.adhar.kubernetes.health;

import com.adhar.kubernetes.config.KubernetesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.NamedContributor;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Health contributor for Kubernetes integration.
 * Provides health indicators for liveness, readiness, and startup probes.
 */
@RequiredArgsConstructor
public class KubernetesHealthContributor implements HealthContributor {

    private final KubernetesProperties properties;
    private final Map<String, HealthContributor> contributors;

    /**
     * Constructor with properties.
     *
     * @param properties Kubernetes properties
     */
    public KubernetesHealthContributor(KubernetesProperties properties) {
        this.properties = properties;
        this.contributors = createContributors();
    }

    /**
     * Create health contributors for Kubernetes probes.
     *
     * @return map of health contributors
     */
    private Map<String, HealthContributor> createContributors() {
        if (!properties.isEnabled() || !properties.getProbes().isEnabled()) {
            return Collections.emptyMap();
        }

        return Stream.of(
                Map.entry("liveness", new KubernetesLivenessHealthIndicator()),
                Map.entry("readiness", new KubernetesReadinessHealthIndicator()),
                Map.entry("startup", new KubernetesStartupHealthIndicator())
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public HealthContributor getContributor(String name) {
        return contributors.get(name);
    }

    @Override
    public Stream<NamedContributor<HealthContributor>> stream() {
        return contributors.entrySet().stream()
                .map(entry -> NamedContributor.of(entry.getKey(), entry.getValue()));
    }

    /**
     * Health indicator for Kubernetes liveness probe.
     */
    private class KubernetesLivenessHealthIndicator extends AbstractHealthIndicator {
        @Override
        protected void doHealthCheck(Health.Builder builder) {
            builder.up()
                    .withDetail("probe", "liveness")
                    .withDetail("path", properties.getProbes().getLivenessPath());
        }
    }

    /**
     * Health indicator for Kubernetes readiness probe.
     */
    private class KubernetesReadinessHealthIndicator extends AbstractHealthIndicator {
        @Override
        protected void doHealthCheck(Health.Builder builder) {
            builder.up()
                    .withDetail("probe", "readiness")
                    .withDetail("path", properties.getProbes().getReadinessPath());
        }
    }

    /**
     * Health indicator for Kubernetes startup probe.
     */
    private class KubernetesStartupHealthIndicator extends AbstractHealthIndicator {
        @Override
        protected void doHealthCheck(Health.Builder builder) {
            builder.up()
                    .withDetail("probe", "startup")
                    .withDetail("path", properties.getProbes().getStartupPath());
        }
    }
}