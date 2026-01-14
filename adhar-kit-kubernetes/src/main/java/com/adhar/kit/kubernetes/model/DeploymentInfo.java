package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Deployment information model.
 *
 * <p>Contains metadata and status information about a Kubernetes deployment.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfo {

    /**
     * Deployment name.
     */
    private String name;

    /**
     * Deployment namespace.
     */
    private String namespace;

    /**
     * Desired replica count.
     */
    private Integer replicas;

    /**
     * Ready replica count.
     */
    private Integer readyReplicas;

    /**
     * Available replica count.
     */
    private Integer availableReplicas;

    /**
     * Unavailable replica count.
     */
    private Integer unavailableReplicas;

    /**
     * Deployment labels.
     */
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    /**
     * Pod selector labels.
     */
    @Builder.Default
    private Map<String, String> selector = new HashMap<>();

    /**
     * Whether deployment is paused.
     */
    private boolean paused;

    /**
     * Checks if deployment is ready.
     *
     * @return true if all replicas are ready
     */
    public boolean isReady() {
        return readyReplicas != null && replicas != null &&
               readyReplicas.equals(replicas);
    }

    /**
     * Gets deployment health percentage.
     *
     * @return percentage of ready replicas (0-100)
     */
    public int getHealthPercentage() {
        if (replicas == null || replicas == 0) {
            return 0;
        }
        if (readyReplicas == null) {
            return 0;
        }
        return (readyReplicas * 100) / replicas;
    }
}

