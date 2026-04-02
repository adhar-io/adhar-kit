package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka health indicator.
 *
 * <p>Checks Kafka broker connectivity using Kafka AdminClient.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Broker connectivity validation</li>
 *   <li>Reports cluster ID</li>
 *   <li>Reports broker count</li>
 *   <li>Reports controller node information</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * KafkaHealthIndicator indicator = new KafkaHealthIndicator(
 *     adminClient,
 *     properties.getKafka()
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     kafka:
 *       enabled: true
 *       timeout: 5000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class KafkaHealthIndicator implements AdharHealthIndicator {

    private final Object adminClient;
    private final AdharHealthProperties.KafkaConfig config;

    /**
     * Creates Kafka health indicator.
     *
     * @param adminClient Kafka AdminClient instance
     * @param config kafka health configuration
     */
    public KafkaHealthIndicator(Object adminClient,
                                AdharHealthProperties.KafkaConfig config) {
        this.adminClient = adminClient;
        this.config = config;
    }

    @Override
    public Health check() {
        if (!config.isEnabled()) {
            return Health.unknown()
                .component(getName())
                .withDetail("status", "disabled")
                .build();
        }

        try {
            // Use reflection to avoid hard dependency on Kafka client
            // Describe cluster
            Method describeClusterMethod = adminClient.getClass().getMethod("describeCluster");
            Object describeClusterResult = describeClusterMethod.invoke(adminClient);

            // Get cluster ID
            Method clusterIdMethod = describeClusterResult.getClass().getMethod("clusterId");
            Object clusterIdFuture = clusterIdMethod.invoke(describeClusterResult);

            String clusterId = getFutureResult(clusterIdFuture, config.getTimeout());

            // Get nodes (brokers)
            Method nodesMethod = describeClusterResult.getClass().getMethod("nodes");
            Object nodesFuture = nodesMethod.invoke(describeClusterResult);

            Collection<?> nodes = getFutureResult(nodesFuture, config.getTimeout());
            int brokerCount = nodes != null ? nodes.size() : 0;

            // Get controller
            Method controllerMethod = describeClusterResult.getClass().getMethod("controller");
            Object controllerFuture = controllerMethod.invoke(describeClusterResult);

            Object controller = getFutureResult(controllerFuture, config.getTimeout());
            String controllerInfo = "unknown";
            if (controller != null) {
                Method idMethod = controller.getClass().getMethod("id");
                Method hostMethod = controller.getClass().getMethod("host");
                Method portMethod = controller.getClass().getMethod("port");

                int controllerId = (int) idMethod.invoke(controller);
                String controllerHost = (String) hostMethod.invoke(controller);
                int controllerPort = (int) portMethod.invoke(controller);

                controllerInfo = String.format("%s:%d (id=%d)", controllerHost, controllerPort, controllerId);
            }

            if (brokerCount == 0) {
                return Health.down()
                    .component(getName())
                    .withDetail("error", "No brokers available")
                    .withDetail("clusterId", clusterId)
                    .build();
            }

            return Health.up()
                .component(getName())
                .withDetail("clusterId", clusterId)
                .withDetail("brokerCount", brokerCount)
                .withDetail("controller", controllerInfo)
                .build();

        } catch (TimeoutException e) {
            log.error("Kafka health check timed out", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", "Connection timed out")
                .build();
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            Throwable cause = e;
            if (e instanceof ExecutionException && e.getCause() != null) {
                cause = e.getCause();
            }
            return Health.down(new Exception(cause.getMessage()))
                .component(getName())
                .withDetail("error", cause.getMessage())
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFutureResult(Object future, long timeoutMs) throws Exception {
        Method getMethod = future.getClass().getMethod("get", long.class, TimeUnit.class);
        return (T) getMethod.invoke(future, timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getName() {
        return "kafka";
    }
}
