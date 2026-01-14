package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.DeploymentInfo;
import com.adhar.kit.kubernetes.model.ReplicaSetInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Service for managing Kubernetes deployments.
 *
 * <p>Provides comprehensive deployment management:</p>
 * <ul>
 *   <li>Create and update deployments</li>
 *   <li>Scale deployments</li>
 *   <li>Rolling updates</li>
 *   <li>Rollback deployments</li>
 *   <li>Deployment status monitoring</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private DeploymentService deploymentService;
 *
 * // Scale deployment
 * deploymentService.scaleDeployment("order-service", 5);
 *
 * // Check deployment status
 * DeploymentInfo info = deploymentService.getDeployment("order-service");
 *
 * // Rollback deployment
 * deploymentService.rollbackDeployment("order-service");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DeploymentService {

    private final KubernetesClient kubernetesClient;
    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates deployment service.
     *
     * @param kubernetesClient Kubernetes client
     */
    public DeploymentService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.client = createFabric8Client();
    }

    /**
     * Gets deployment information.
     *
     * @param name deployment name
     * @return deployment information
     */
    public DeploymentInfo getDeployment(String name) {
        try {
            io.fabric8.kubernetes.api.model.apps.Deployment deployment = client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .get();

            if (deployment == null) {
                log.warn("Deployment {} not found", name);
                return null;
            }

            return toDeploymentInfo(deployment);

        } catch (Exception e) {
            log.error("Failed to get deployment {}", name, e);
            return null;
        }
    }

    /**
     * Lists all deployments.
     *
     * @return list of deployments
     */
    public List<DeploymentInfo> listDeployments() {
        return listDeployments(null);
    }

    /**
     * Lists deployments by label selector.
     *
     * @param labelSelector label selector
     * @return list of deployments
     */
    public List<DeploymentInfo> listDeployments(String labelSelector) {
        try {
            io.fabric8.kubernetes.api.model.apps.DeploymentList deploymentList;

            if (labelSelector != null && !labelSelector.isEmpty()) {
                deploymentList = client.apps()
                    .deployments()
                    .inNamespace(kubernetesClient.getNamespace())
                    .withLabel(labelSelector)
                    .list();
            } else {
                deploymentList = client.apps()
                    .deployments()
                    .inNamespace(kubernetesClient.getNamespace())
                    .list();
            }

            return deploymentList.getItems().stream()
                .map(this::toDeploymentInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to list deployments", e);
            return List.of();
        }
    }

    /**
     * Scales a deployment.
     *
     * @param name deployment name
     * @param replicas desired replica count
     * @return true if successful
     */
    public boolean scaleDeployment(String name, int replicas) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .scale(replicas);

            log.info("Scaled deployment {} to {} replicas", name, replicas);
            return true;

        } catch (Exception e) {
            log.error("Failed to scale deployment {}", name, e);
            return false;
        }
    }

    /**
     * Restarts a deployment (triggers rolling update).
     *
     * @param name deployment name
     * @return true if successful
     */
    public boolean restartDeployment(String name) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .rolling()
                .restart();

            log.info("Restarted deployment {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to restart deployment {}", name, e);
            return false;
        }
    }

    /**
     * Pauses a deployment.
     *
     * @param name deployment name
     * @return true if successful
     */
    public boolean pauseDeployment(String name) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .rolling()
                .pause();

            log.info("Paused deployment {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to pause deployment {}", name, e);
            return false;
        }
    }

    /**
     * Resumes a paused deployment.
     *
     * @param name deployment name
     * @return true if successful
     */
    public boolean resumeDeployment(String name) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .rolling()
                .resume();

            log.info("Resumed deployment {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to resume deployment {}", name, e);
            return false;
        }
    }

    /**
     * Rolls back a deployment.
     *
     * @param name deployment name
     * @return true if successful
     */
    public boolean rollbackDeployment(String name) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .rolling()
                .undo();

            log.info("Rolled back deployment {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to rollback deployment {}", name, e);
            return false;
        }
    }

    /**
     * Updates deployment image.
     *
     * @param name deployment name
     * @param containerName container name
     * @param newImage new image
     * @return true if successful
     */
    public boolean updateImage(String name, String containerName, String newImage) {
        try {
            client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .rolling()
                .updateImage(Map.of(containerName, newImage));

            log.info("Updated image for deployment {} container {} to {}",
                name, containerName, newImage);
            return true;

        } catch (Exception e) {
            log.error("Failed to update image for deployment {}", name, e);
            return false;
        }
    }

    /**
     * Checks if deployment is ready.
     *
     * @param name deployment name
     * @return true if ready
     */
    public boolean isDeploymentReady(String name) {
        try {
            io.fabric8.kubernetes.api.model.apps.Deployment deployment = client.apps()
                .deployments()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .get();

            if (deployment == null) {
                return false;
            }

            Integer replicas = deployment.getSpec().getReplicas();
            Integer readyReplicas = deployment.getStatus().getReadyReplicas();

            return readyReplicas != null && replicas != null &&
                   readyReplicas.equals(replicas);

        } catch (Exception e) {
            log.error("Failed to check deployment readiness for {}", name, e);
            return false;
        }
    }

    /**
     * Gets replica sets for a deployment.
     *
     * @param name deployment name
     * @return list of replica sets
     */
    public List<ReplicaSetInfo> getReplicaSets(String name) {
        try {
            io.fabric8.kubernetes.api.model.apps.ReplicaSetList replicaSets = client.apps()
                .replicaSets()
                .inNamespace(kubernetesClient.getNamespace())
                .withLabel("app", name)
                .list();

            return replicaSets.getItems().stream()
                .map(this::toReplicaSetInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to get replica sets for deployment {}", name, e);
            return List.of();
        }
    }

    /**
     * Creates Fabric8 Kubernetes client.
     */
    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
    }

    /**
     * Converts Fabric8 Deployment to DeploymentInfo.
     */
    private DeploymentInfo toDeploymentInfo(io.fabric8.kubernetes.api.model.apps.Deployment deployment) {
        return DeploymentInfo.builder()
            .name(deployment.getMetadata().getName())
            .namespace(deployment.getMetadata().getNamespace())
            .replicas(deployment.getSpec().getReplicas())
            .readyReplicas(deployment.getStatus().getReadyReplicas())
            .availableReplicas(deployment.getStatus().getAvailableReplicas())
            .unavailableReplicas(deployment.getStatus().getUnavailableReplicas())
            .labels(deployment.getMetadata().getLabels())
            .selector(deployment.getSpec().getSelector().getMatchLabels())
            .paused(deployment.getSpec().getPaused() != null && deployment.getSpec().getPaused())
            .build();
    }

    /**
     * Converts Fabric8 ReplicaSet to ReplicaSetInfo.
     */
    private ReplicaSetInfo toReplicaSetInfo(io.fabric8.kubernetes.api.model.apps.ReplicaSet replicaSet) {
        return ReplicaSetInfo.builder()
            .name(replicaSet.getMetadata().getName())
            .namespace(replicaSet.getMetadata().getNamespace())
            .replicas(replicaSet.getSpec().getReplicas())
            .readyReplicas(replicaSet.getStatus().getReadyReplicas())
            .labels(replicaSet.getMetadata().getLabels())
            .build();
    }
}

