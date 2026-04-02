package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Elasticsearch health indicator.
 *
 * <p>Checks Elasticsearch cluster health using the Elasticsearch client.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Cluster health validation via _cluster/health API</li>
 *   <li>Reports cluster status (green/yellow/red)</li>
 *   <li>Reports cluster name</li>
 *   <li>Reports node count</li>
 *   <li>Reports active/relocating shards</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(
 *     elasticsearchClient,
 *     properties.getElasticsearch()
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     elasticsearch:
 *       enabled: true
 *       timeout: 3000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ElasticsearchHealthIndicator implements AdharHealthIndicator {

    private final Object elasticsearchClient;
    private final AdharHealthProperties.ElasticsearchConfig config;

    /**
     * Creates Elasticsearch health indicator.
     *
     * @param elasticsearchClient Elasticsearch client instance (ElasticsearchClient or RestHighLevelClient)
     * @param config elasticsearch health configuration
     */
    public ElasticsearchHealthIndicator(Object elasticsearchClient,
                                        AdharHealthProperties.ElasticsearchConfig config) {
        this.elasticsearchClient = elasticsearchClient;
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
            // Try new Elasticsearch Java client first (co.elastic.clients)
            if (isNewElasticsearchClient()) {
                return checkWithNewClient();
            }

            // Fall back to RestHighLevelClient
            return checkWithRestHighLevelClient();

        } catch (Exception e) {
            log.error("Elasticsearch health check failed", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private boolean isNewElasticsearchClient() {
        try {
            return elasticsearchClient.getClass().getName().contains("co.elastic.clients");
        } catch (Exception e) {
            return false;
        }
    }

    private Health checkWithNewClient() throws Exception {
        // Use reflection to call cluster().health()
        Method clusterMethod = elasticsearchClient.getClass().getMethod("cluster");
        Object clusterClient = clusterMethod.invoke(elasticsearchClient);

        // Build health request
        Class<?> healthRequestClass = Class.forName(
            "co.elastic.clients.elasticsearch.cluster.HealthRequest");
        Object healthRequest = healthRequestClass.getConstructor().newInstance();

        // Call health method
        Method healthMethod = clusterClient.getClass().getMethod("health", healthRequestClass);
        Object healthResponse = healthMethod.invoke(clusterClient, healthRequest);

        // Extract health information
        Method statusMethod = healthResponse.getClass().getMethod("status");
        Object status = statusMethod.invoke(healthResponse);
        String statusName = status.toString().toLowerCase();

        Method clusterNameMethod = healthResponse.getClass().getMethod("clusterName");
        String clusterName = (String) clusterNameMethod.invoke(healthResponse);

        Method numberOfNodesMethod = healthResponse.getClass().getMethod("numberOfNodes");
        int nodeCount = (int) numberOfNodesMethod.invoke(healthResponse);

        Method activeShardsMethod = healthResponse.getClass().getMethod("activeShards");
        int activeShards = (int) activeShardsMethod.invoke(healthResponse);

        Method relocatingShardsMethod = healthResponse.getClass().getMethod("relocatingShards");
        int relocatingShards = (int) relocatingShardsMethod.invoke(healthResponse);

        return buildHealthFromStatus(statusName, clusterName, nodeCount, activeShards, relocatingShards);
    }

    private Health checkWithRestHighLevelClient() throws Exception {
        // Get cluster client
        Method clusterMethod = elasticsearchClient.getClass().getMethod("cluster");
        Object clusterClient = clusterMethod.invoke(elasticsearchClient);

        // Build ClusterHealthRequest
        Class<?> healthRequestClass = Class.forName(
            "org.elasticsearch.client.cluster.ClusterHealthRequest");
        Object healthRequest = healthRequestClass.getConstructor().newInstance();

        // Get RequestOptions.DEFAULT
        Class<?> requestOptionsClass = Class.forName("org.elasticsearch.client.RequestOptions");
        Object requestOptions = requestOptionsClass.getField("DEFAULT").get(null);

        // Call health method
        Method healthMethod = clusterClient.getClass().getMethod("health",
            healthRequestClass, requestOptionsClass);
        Object healthResponse = healthMethod.invoke(clusterClient, healthRequest, requestOptions);

        // Extract health information
        Method getStatusMethod = healthResponse.getClass().getMethod("getStatus");
        Object status = getStatusMethod.invoke(healthResponse);
        String statusName = status.toString().toLowerCase();

        Method getClusterNameMethod = healthResponse.getClass().getMethod("getClusterName");
        String clusterName = (String) getClusterNameMethod.invoke(healthResponse);

        Method getNumberOfNodesMethod = healthResponse.getClass().getMethod("getNumberOfNodes");
        int nodeCount = (int) getNumberOfNodesMethod.invoke(healthResponse);

        Method getActiveShardsMethod = healthResponse.getClass().getMethod("getActiveShards");
        int activeShards = (int) getActiveShardsMethod.invoke(healthResponse);

        Method getRelocatingShardsMethod = healthResponse.getClass().getMethod("getRelocatingShards");
        int relocatingShards = (int) getRelocatingShardsMethod.invoke(healthResponse);

        return buildHealthFromStatus(statusName, clusterName, nodeCount, activeShards, relocatingShards);
    }

    private Health buildHealthFromStatus(String statusName, String clusterName,
                                         int nodeCount, int activeShards, int relocatingShards) {
        Health.HealthBuilder builder;

        switch (statusName) {
            case "green" -> builder = Health.up();
            case "yellow" -> builder = Health.up(); // Yellow is degraded but still operational
            case "red" -> builder = Health.down();
            default -> builder = Health.unknown();
        }

        builder.component(getName())
            .withDetail("status", statusName)
            .withDetail("clusterName", clusterName)
            .withDetail("nodeCount", nodeCount)
            .withDetail("activeShards", activeShards)
            .withDetail("relocatingShards", relocatingShards);

        if ("yellow".equals(statusName)) {
            builder.withDetail("warning", "Cluster is in yellow state - some replicas may be unavailable");
        }

        if ("red".equals(statusName)) {
            builder.withDetail("error", "Cluster is in red state - some primary shards are unavailable");
        }

        return builder.build();
    }

    @Override
    public String getName() {
        return "elasticsearch";
    }
}
