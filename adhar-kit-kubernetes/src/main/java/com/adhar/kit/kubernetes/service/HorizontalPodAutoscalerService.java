package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.annotation.KubernetesAutoScale;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs {@link KubernetesAutoScale} by reconciling a real
 * {@code autoscaling/v2} {@link HorizontalPodAutoscaler} from the annotation's
 * min/max replica and target-utilization attributes.
 *
 * <p>Uses create-or-update ("apply") semantics: {@link #reconcile} always writes the
 * fully-desired HPA spec, so repeated calls (e.g. on every reconciliation tick) converge
 * on the same result.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private HorizontalPodAutoscalerService hpaService;
 *
 * KubernetesAutoScale config = ...; // from the annotation
 * hpaService.reconcile("order-service", "default", config);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class HorizontalPodAutoscalerService {

    private static final String CPU_METRIC = "cpu";
    private static final String MEMORY_METRIC = "memory";
    private static final String RESOURCE_METRIC_TYPE = "Resource";
    private static final String UTILIZATION_TARGET_TYPE = "Utilization";

    private final KubernetesClient kubernetesClient;
    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates the HPA reconciliation service.
     *
     * @param kubernetesClient Kubernetes client wrapper (used for its configured namespace)
     */
    public HorizontalPodAutoscalerService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.client = createFabric8Client();
    }

    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
    }

    /**
     * Reconciles the HPA for {@code deploymentName} in the wrapped client's configured
     * namespace.
     *
     * @param deploymentName name of the Deployment to scale (also used as the HPA name)
     * @param config         the {@link KubernetesAutoScale} annotation attributes
     * @return true if the HPA was successfully created/updated
     */
    public boolean reconcile(String deploymentName, KubernetesAutoScale config) {
        return reconcile(deploymentName, kubernetesClient.getNamespace(), config);
    }

    /**
     * Reconciles (creates or updates) the HPA for {@code deploymentName}.
     *
     * @param deploymentName name of the Deployment to scale (also used as the HPA name)
     * @param namespace      namespace the Deployment/HPA live in
     * @param config         the {@link KubernetesAutoScale} annotation attributes
     * @return true if the HPA was successfully created/updated, false on error
     */
    public boolean reconcile(String deploymentName, String namespace, KubernetesAutoScale config) {
        try {
            HorizontalPodAutoscaler desired = buildHorizontalPodAutoscaler(deploymentName, namespace, config);

            client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .resource(desired)
                    .createOrReplace();

            log.info("Reconciled HorizontalPodAutoscaler for deployment {}/{} (min={}, max={}, targetCpu={}%)",
                    namespace, deploymentName, config.minReplicas(), config.maxReplicas(),
                    config.targetCpuUtilization());
            return true;
        } catch (Exception e) {
            log.error("Failed to reconcile HorizontalPodAutoscaler for deployment {}/{}",
                    namespace, deploymentName, e);
            return false;
        }
    }

    /**
     * Builds the desired {@link HorizontalPodAutoscaler} resource from the annotation
     * attributes, without talking to the cluster. Exposed for testing the payload shape.
     *
     * @param deploymentName name of the Deployment to scale (also used as the HPA name)
     * @param namespace      namespace the Deployment/HPA live in
     * @param config         the {@link KubernetesAutoScale} annotation attributes
     * @return the desired HPA resource
     */
    public HorizontalPodAutoscaler buildHorizontalPodAutoscaler(String deploymentName, String namespace,
                                                                  KubernetesAutoScale config) {
        List<MetricSpec> metrics = new ArrayList<>();
        metrics.add(resourceMetric(CPU_METRIC, config.targetCpuUtilization()));
        if (config.targetMemoryUtilization() > 0) {
            metrics.add(resourceMetric(MEMORY_METRIC, config.targetMemoryUtilization()));
        }

        return new HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                    .withName(deploymentName)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewScaleTargetRef("apps/v1", "Deployment", deploymentName)
                    .withMinReplicas(config.minReplicas())
                    .withMaxReplicas(config.maxReplicas())
                    .withMetrics(metrics)
                    .withNewBehavior()
                        .withNewScaleUp()
                            .withStabilizationWindowSeconds(config.scaleUpStabilization())
                        .endScaleUp()
                        .withNewScaleDown()
                            .withStabilizationWindowSeconds(config.scaleDownStabilization())
                        .endScaleDown()
                    .endBehavior()
                .endSpec()
                .build();
    }

    private MetricSpec resourceMetric(String resourceName, int targetUtilizationPercent) {
        return new MetricSpecBuilder()
                .withType(RESOURCE_METRIC_TYPE)
                .withNewResource()
                    .withName(resourceName)
                    .withNewTarget()
                        .withType(UTILIZATION_TARGET_TYPE)
                        .withAverageUtilization(targetUtilizationPercent)
                    .endTarget()
                .endResource()
                .build();
    }

    /**
     * Fetches the current HPA for a deployment, if any.
     *
     * @param deploymentName the deployment/HPA name
     * @param namespace      namespace the HPA lives in
     * @return the HPA, or null if not found or on error
     */
    public HorizontalPodAutoscaler getHorizontalPodAutoscaler(String deploymentName, String namespace) {
        try {
            return client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();
        } catch (Exception e) {
            log.error("Failed to get HorizontalPodAutoscaler {}/{}", namespace, deploymentName, e);
            return null;
        }
    }

    /**
     * Deletes the HPA for a deployment, if present.
     *
     * @param deploymentName the deployment/HPA name
     * @param namespace      namespace the HPA lives in
     * @return true if the delete call succeeded
     */
    public boolean delete(String deploymentName, String namespace) {
        try {
            client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .delete();
            return true;
        } catch (Exception e) {
            log.error("Failed to delete HorizontalPodAutoscaler {}/{}", namespace, deploymentName, e);
            return false;
        }
    }
}
